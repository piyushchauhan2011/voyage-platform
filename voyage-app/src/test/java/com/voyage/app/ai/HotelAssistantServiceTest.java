package com.voyage.app.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wires the assistant to stub models so the RAG plumbing can be verified with no API key,
 * no network, and no cost. What is under test is our orchestration — that retrieval runs,
 * that its results reach the prompt and the trace, and that memory is threaded through —
 * not Gemini's answer quality.
 */
class HotelAssistantServiceTest {

    private RecordingChatModel chatModel;
    private StubVectorStore vectorStore;
    private HotelAssistantService assistant;

    @BeforeEach
    void setUp() {
        chatModel = new RecordingChatModel("Driftwood Rooms in Lisbon is $74 per night.");
        vectorStore = new StubVectorStore(List.of(
                new Document("hotel-1", "Driftwood Rooms in Lisbon. Steps from the sand.",
                        Map.of("hotelId", 1L, "name", "Driftwood Rooms", "city", "Lisbon", "pricePerNight", 74.0)),
                new Document("hotel-2", "Salt & Pine Guesthouse in Lisbon. Two minutes from the surf.",
                        Map.of("hotelId", 2L, "name", "Salt & Pine Guesthouse", "city", "Lisbon", "pricePerNight", 82.0))));
        assistant = new HotelAssistantService(
                ChatClient.builder(chatModel), vectorStore, new HotelTools(null, null), configuredProperties());
    }

    @Test
    void ask_returnsTheModelAnswer() {
        HotelAssistantService.AssistantResult result = assistant.ask("Find hotels near beach under $100", null);

        assertThat(result.answer()).isEqualTo("Driftwood Rooms in Lisbon is $74 per night.");
    }

    @Test
    void ask_runsRetrievalAndReportsTheDocumentsItUsed() {
        HotelAssistantService.AssistantResult result = assistant.ask("Find hotels near beach under $100", null);

        assertThat(vectorStore.searchCount).isEqualTo(1);
        assertThat(result.retrievedDocuments())
                .extracting(VectorSearchService.Match::name)
                .containsExactly("Driftwood Rooms", "Salt & Pine Guesthouse");
    }

    @Test
    void ask_pastesRetrievedContextIntoThePromptSentToTheModel() {
        assistant.ask("Find hotels near beach under $100", null);

        // The point of RAG: the model must physically see the hotel text in its prompt.
        assertThat(chatModel.lastPromptText()).contains("Driftwood Rooms", "Steps from the sand");
    }

    @Test
    void ask_appliesConfiguredTopK() {
        assistant.ask("anything", null);

        assertThat(vectorStore.lastRequest.getTopK()).isEqualTo(3);
        assertThat(vectorStore.lastRequest.getSimilarityThreshold()).isEqualTo(0.4);
    }

    @Test
    void ask_defaultsTheQuestionWhenBlank() {
        HotelAssistantService.AssistantResult result = assistant.ask("   ", null);

        assertThat(result.question()).isEqualTo("Find hotels near the beach under $100");
    }

    @Test
    void ask_carriesEarlierTurnsIntoLaterOnesForTheSameConversation() {
        assistant.ask("Find hotels near beach under $100", "conversation-a");
        assistant.ask("Which of those has wifi?", "conversation-a");

        // The second call must see the first exchange, otherwise "those" is unanswerable.
        assertThat(chatModel.lastPromptText()).contains("Find hotels near beach under $100");
    }

    @Test
    void ask_keepsSeparateConversationsIsolated() {
        assistant.ask("Find hotels near beach under $100", "conversation-a");
        assistant.ask("Which of those has wifi?", "conversation-b");

        assertThat(chatModel.lastPromptText()).doesNotContain("Find hotels near beach under $100");
    }

    @Test
    void clearConversation_dropsEarlierTurns() {
        assistant.ask("Find hotels near beach under $100", "conversation-a");
        assistant.clearConversation("conversation-a");
        assistant.ask("Which of those has wifi?", "conversation-a");

        assertThat(chatModel.lastPromptText()).doesNotContain("Find hotels near beach under $100");
    }

    @Test
    void ask_failsWithAReadableMessageWhenNoApiKeyIsConfigured() {
        HotelAssistantService unconfigured = new HotelAssistantService(
                ChatClient.builder(chatModel), vectorStore, new HotelTools(null, null), unconfiguredProperties());

        assertThatThrownBy(() -> unconfigured.ask("anything", null))
                .isInstanceOf(AiNotConfiguredException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    private static AiLabProperties configuredProperties() {
        return new AiLabProperties("test-key", "gemini-2.5-flash", "text-embedding-004", 3, 0.4);
    }

    private static AiLabProperties unconfiguredProperties() {
        return new AiLabProperties(AiLabProperties.UNCONFIGURED_KEY, "gemini-2.5-flash",
                "text-embedding-004", 3, 0.4);
    }

    /** Returns a fixed answer and remembers the prompt it was handed. */
    private static final class RecordingChatModel implements ChatModel {

        private final String answer;
        private Prompt lastPrompt;

        private RecordingChatModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        }

        String lastPromptText() {
            return lastPrompt.getInstructions().stream()
                    .map(message -> message.getText() == null ? "" : message.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }

    /** Returns fixed documents and records the search requests it was given. */
    private static final class StubVectorStore implements VectorStore {

        private final List<Document> documents;
        private final List<Document> added = new ArrayList<>();
        private SearchRequest lastRequest;
        private int searchCount;

        private StubVectorStore(List<Document> documents) {
            this.documents = documents;
        }

        @Override
        public void add(List<Document> documents) {
            added.addAll(documents);
        }

        @Override
        public void delete(List<String> ids) {
            added.removeIf(document -> ids.contains(document.getId()));
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            // not exercised by these tests
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            this.lastRequest = request;
            this.searchCount++;
            return documents;
        }
    }
}
