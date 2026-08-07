package com.voyage.app.ai;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the real pgvector round trip: schema creation, insert, and cosine ranking,
 * against the same {@code pgvector/pgvector:pg16} image docker-compose uses.
 *
 * <p>The embedding model is a deterministic fake rather than Gemini. That keeps the test free,
 * offline, and repeatable while still exercising everything we actually own — the document
 * shape, the metadata filter, and the idempotent re-ingest. Vector arithmetic itself is
 * Postgres's job and does not need our verification.
 *
 * <p>Prefers Testcontainers. If the Docker API is unreachable (common with some Docker Desktop
 * socket setups, the same reason {@code RabbitMqIntegrationTestSupport} has a fallback), it uses
 * a pgvector instance on {@code localhost:5433}, which you can start with:
 *
 * <pre>{@code
 * docker run -d --name voyage-pgvector-test -p 5433:5432 \
 *   -e POSTGRES_DB=voyage_vector_test -e POSTGRES_USER=voyage -e POSTGRES_PASSWORD=voyage \
 *   pgvector/pgvector:pg16
 * }</pre>
 *
 * <p>Skipped entirely when neither is available, so the default build stays green without Docker.
 * Port 5433 is deliberate: the dev database on 5432 must not be wiped by a test.
 */
@SpringBootTest
@ActiveProfiles("test")
class PgVectorIntegrationTest {

    /** Small enough to reason about, and matches the dimensions property set below. */
    private static final int FAKE_EMBEDDING_DIMENSIONS = 8;

    private static final int FALLBACK_PORT = 5433;
    private static final String FALLBACK_URL =
            "jdbc:postgresql://localhost:" + FALLBACK_PORT + "/voyage_vector_test";

    private static final boolean DOCKER_API_AVAILABLE = isDockerApiAvailable();
    private static final boolean FALLBACK_REACHABLE = !DOCKER_API_AVAILABLE && isFallbackReachable();

    static final PostgreSQLContainer<?> POSTGRES = DOCKER_API_AVAILABLE
            ? new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            : null;

    static {
        if (POSTGRES != null) {
            POSTGRES.start();
        }
    }

    @BeforeAll
    static void requirePgvector() {
        Assumptions.assumeTrue(DOCKER_API_AVAILABLE || FALLBACK_REACHABLE,
                "pgvector tests need Testcontainers Docker or a pgvector instance on localhost:" + FALLBACK_PORT);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Swap H2 for real Postgres, and re-enable the store the test profile excludes.
        if (DOCKER_API_AVAILABLE) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> FALLBACK_URL);
            registry.add("spring.datasource.username", () -> "voyage");
            registry.add("spring.datasource.password", () -> "voyage");
        }
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.autoconfigure.exclude", () -> "");
        registry.add("spring.ai.model.chat", () -> "none");
        registry.add("spring.ai.model.embedding.text", () -> "none");
        // Satisfies the "is a key present" guard. Nothing here calls Google — the stub models do the work.
        registry.add("spring.ai.google.genai.api-key", () -> "test-key");
        registry.add("spring.ai.vectorstore.pgvector.initialize-schema", () -> true);
        registry.add("spring.ai.vectorstore.pgvector.dimensions", () -> FAKE_EMBEDDING_DIMENSIONS);
        registry.add("spring.ai.vectorstore.pgvector.index-type", () -> "HNSW");
        registry.add("spring.ai.vectorstore.pgvector.distance-type", () -> "COSINE_DISTANCE");
        registry.add("application.ai.enabled", () -> true);
        registry.add("application.kafka.enabled", () -> false);
        registry.add("application.redis.enabled", () -> false);
        registry.add("application.rabbitmq.enabled", () -> false);
    }

    @Autowired VectorStore vectorStore;
    @Autowired HotelRepository hotelRepository;
    @Autowired HotelDocumentIngestor ingestor;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // The vector table is not JPA-managed, so ddl-auto never resets it between tests.
        jdbcTemplate.execute("TRUNCATE TABLE vector_store");
        hotelRepository.deleteAll();
        hotelRepository.save(new Hotel("Driftwood Rooms", "Lisbon", 74.0,
                "Budget rooms a barefoot walk from a wide stretch of sand and surf.", "free wifi"));
        hotelRepository.save(new Hotel("Azure Sands Resort", "Nice", 320.0,
                "Large shorefront resort with private access to the sand.", "spa, pools"));
        hotelRepository.save(new Hotel("Alpine Ridge Chalet", "Innsbruck", 145.0,
                "Timber chalet at the base of the ski lifts, with a boot drying room.", "sauna"));
    }

    @Test
    void ingestWritesEveryHotelIntoPgvector() {
        HotelDocumentIngestor.IngestResult result = ingestor.ingest();

        assertThat(result.documentsIngested()).isEqualTo(3);
        assertThat(storedRowCount()).isEqualTo(3);
    }

    @Test
    void reingestingReplacesRatherThanDuplicates() {
        ingestor.ingest();
        ingestor.ingest();

        // Without the delete-by-id step this would be six rows and every score would be skewed.
        assertThat(storedRowCount()).isEqualTo(3);
    }

    @Test
    void metadataFilterExcludesCandidatesBeforeRanking() {
        ingestor.ingest();

        List<Document> affordable = vectorStore.similaritySearch(SearchRequest.builder()
                .query("somewhere near the sand")
                .topK(10)
                .similarityThresholdAll()
                .filterExpression("pricePerNight < 100")
                .build());

        assertThat(affordable)
                .isNotEmpty()
                .allSatisfy(document ->
                        assertThat((Double) document.getMetadata().get("pricePerNight")).isLessThan(100.0));
    }

    @Test
    void storedMetadataSurvivesTheRoundTripThroughPostgres() {
        ingestor.ingest();

        Document document = vectorStore.similaritySearch(SearchRequest.builder()
                        .query("ski lifts")
                        .topK(10)
                        .similarityThresholdAll()
                        .filterExpression("city == 'Innsbruck'")
                        .build())
                .getFirst();

        assertThat(document.getMetadata()).containsEntry("city", "Innsbruck");
        assertThat(document.getText()).contains("Alpine Ridge Chalet");
    }

    @Test
    void ingestFailsClearlyWhenThereIsNothingToEmbed() {
        hotelRepository.deleteAll();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ingestor.ingest())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Seed catalog");
    }

    private static boolean isDockerApiAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isFallbackReachable() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", FALLBACK_PORT), 500);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Counts rows in the table rather than via similarity search: a document whose similarity
     * to the probe is exactly zero is filtered out even by similarityThresholdAll, which would
     * make a search-based count quietly wrong.
     */
    private int storedRowCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Integer.class);
        return count == null ? 0 : count;
    }

    @TestConfiguration
    static class FakeEmbeddingConfiguration {

        /**
         * Enabling the AI lab brings up the chat-backed services too, which need a ChatModel.
         * This test is about the vector store, so the model just has to exist.
         */
        @Bean
        @Primary
        org.springframework.ai.chat.model.ChatModel stubChatModel() {
            return prompt -> new org.springframework.ai.chat.model.ChatResponse(List.of(
                    new org.springframework.ai.chat.model.Generation(
                            new org.springframework.ai.chat.messages.AssistantMessage("stubbed"))));
        }

        /**
         * Hashes each token into a fixed number of buckets — a bag-of-words vector.
         * Crude next to a real model, but deterministic, offline, and enough that documents
         * sharing vocabulary land near each other.
         */
        @Bean
        @Primary
        EmbeddingModel fakeEmbeddingModel() {
            return new EmbeddingModel() {

                @Override
                public EmbeddingResponse call(EmbeddingRequest request) {
                    List<org.springframework.ai.embedding.Embedding> embeddings =
                            new java.util.ArrayList<>(request.getInstructions().size());
                    for (int index = 0; index < request.getInstructions().size(); index++) {
                        embeddings.add(new org.springframework.ai.embedding.Embedding(
                                bagOfWords(request.getInstructions().get(index)), index));
                    }
                    return new EmbeddingResponse(embeddings);
                }

                @Override
                public float[] embed(Document document) {
                    return bagOfWords(document.getText());
                }

                @Override
                public int dimensions() {
                    return FAKE_EMBEDDING_DIMENSIONS;
                }
            };
        }

        private static float[] bagOfWords(String text) {
            float[] vector = new float[FAKE_EMBEDDING_DIMENSIONS];
            for (String token : text.toLowerCase().split("\\W+")) {
                if (!token.isBlank()) {
                    vector[Math.abs(token.hashCode()) % FAKE_EMBEDDING_DIMENSIONS] += 1.0f;
                }
            }
            // pgvector cosine distance is undefined for a zero vector.
            boolean allZero = true;
            for (float value : vector) {
                if (value != 0.0f) {
                    allZero = false;
                    break;
                }
            }
            if (allZero) {
                vector[0] = 1.0f;
            }
            return vector;
        }
    }
}
