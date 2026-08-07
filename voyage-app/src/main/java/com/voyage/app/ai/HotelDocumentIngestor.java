package com.voyage.app.ai;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rung 3 — turns hotel rows into embedded documents in pgvector.
 *
 * Two details worth understanding:
 *
 * 1. Document IDs are derived deterministically from the hotel ID, so re-running ingestion
 *    replaces rather than duplicates. Vector stores have no unique constraint to save you here;
 *    without this, pressing the button twice silently doubles every hotel and skews the results.
 *    PgVectorStore keys on a UUID column, so the id is a name-based UUID rather than "hotel-1".
 *
 * 2. Structured fields (city, price) go into metadata rather than the embedded text. Metadata
 *    is filterable with exact SQL-like predicates, whereas embedded text is only ever matched
 *    fuzzily. Prices in particular must never be left to similarity search — "under $100" is
 *    an arithmetic question, and that is what the tool-calling rung is for.
 */
@Service
@ConditionalOnProperty(name = "application.ai.enabled", havingValue = "true", matchIfMissing = true)
public class HotelDocumentIngestor {

    private static final String DOCUMENT_ID_PREFIX = "voyage-hotel-";

    private final HotelRepository hotelRepository;
    private final VectorStore vectorStore;
    private final AiLabProperties properties;

    public HotelDocumentIngestor(HotelRepository hotelRepository,
                                 VectorStore vectorStore,
                                 AiLabProperties properties) {
        this.hotelRepository = hotelRepository;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public IngestResult ingest() {
        properties.requireApiKey();
        List<Hotel> hotels = hotelRepository.findAll().stream()
                .filter(hotel -> hotel.getDescription() != null && !hotel.getDescription().isBlank())
                .toList();

        if (hotels.isEmpty()) {
            throw new IllegalStateException(
                    "No hotels with descriptions found. Run 'Seed catalog' first — a hotel with no "
                            + "description has nothing to embed.");
        }

        List<String> ids = hotels.stream().map(hotel -> documentId(hotel.getId())).toList();
        // Delete-then-add keeps ingestion idempotent; the store itself will not deduplicate.
        vectorStore.delete(ids);

        List<Document> documents = hotels.stream().map(HotelDocumentIngestor::toDocument).toList();

        long startedAt = System.currentTimeMillis();
        vectorStore.add(documents);
        long tookMs = System.currentTimeMillis() - startedAt;

        return new IngestResult(
                documents.size(),
                properties.embeddingModel(),
                documents.getFirst().getText(),
                documents.getFirst().getMetadata(),
                tookMs,
                "Each hotel became one row in the vector_store table: the text, a 768-float embedding, "
                        + "and JSON metadata. The embedding is what similarity search reads.",
                "The price is in the document text so the model can see it, and in the metadata so filters "
                        + "can compare it. Only the text reaches the prompt; only the metadata is filterable."
        );
    }

    /**
     * Name and city are folded into the embedded text on purpose: a query naming a city should
     * still rank that city's hotels highly even when no metadata filter is supplied.
     *
     * The price appears in both the text and the metadata, for different reasons. Only the text
     * reaches the model — QuestionAnswerAdvisor pastes the document body into the prompt and
     * leaves metadata behind — so without it the RAG rung cannot answer a budget question at all.
     * The metadata copy is what filter expressions compare against.
     *
     * Having the price in the text does not make similarity search able to do arithmetic: the
     * model reads "$74 per night" as prose and reasons about it approximately. That is the gap
     * the tool-calling rung closes.
     */
    static Document toDocument(Hotel hotel) {
        String text = """
                %s in %s.
                Price: $%s per night.
                %s
                Amenities: %s.
                """.formatted(
                hotel.getName(),
                hotel.getCity(),
                hotel.getPricePerNight(),
                hotel.getDescription(),
                hotel.getAmenities() == null ? "not listed" : hotel.getAmenities()).strip();

        Map<String, Object> metadata = Map.of(
                "hotelId", hotel.getId(),
                "name", hotel.getName(),
                "city", hotel.getCity(),
                "pricePerNight", hotel.getPricePerNight());

        return new Document(documentId(hotel.getId()), text, metadata);
    }

    /**
     * Same hotel id always yields the same UUID, which is what makes re-ingest a replace.
     * PgVectorStore's id column is a UUID, so an arbitrary string like "hotel-1" is rejected.
     */
    static String documentId(Long hotelId) {
        return UUID.nameUUIDFromBytes((DOCUMENT_ID_PREFIX + hotelId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public record IngestResult(
            int documentsIngested,
            String embeddingModel,
            String sampleDocumentText,
            Map<String, Object> sampleDocumentMetadata,
            long tookMs,
            String observation,
            String tip
    ) {
    }
}
