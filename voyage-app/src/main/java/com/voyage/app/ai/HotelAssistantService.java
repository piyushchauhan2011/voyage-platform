package com.voyage.app.ai;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Rung 7 — the agent. Everything below the ladder, assembled.
 *
 * <p>"Find hotels near beach under $100" is really two different questions wearing one coat:
 *
 * <p>"near beach" — fuzzy, subjective, answered by embedding similarity over descriptions "under
 * $100" — exact, arithmetic, answered by a SQL query through a tool call
 *
 * <p>Neither mechanism can do the other's job. Similarity search cannot compare numbers, and a SQL
 * WHERE clause cannot tell that "sand and surf" means beach. The agent's value is deciding which to
 * reach for, and combining the results.
 *
 * <p>The response carries a full trace — retrieved documents, tools invoked, token usage — because
 * an agent you cannot inspect is an agent you cannot debug.
 */
@Service
@ConditionalOnProperty(name = "application.ai.enabled", havingValue = "true", matchIfMissing = true)
public class HotelAssistantService {

  private static final String SYSTEM_PROMPT =
      """
            You are the Voyage hotel assistant.

            You have two sources of truth, and you must use both:
            1. Hotel context retrieved by meaning, supplied with the question. Use it to judge
               subjective qualities such as "near the beach", "quiet", or "good for skiing".
            2. Tools that query the live hotel database. You MUST call a tool for anything factual:
               prices, budget limits, and availability for specific dates.

            Never state a price you did not get from a tool. If the retrieved context suggests a
            hotel but a tool shows it is over budget, exclude it and say why.
            Recommend at most four hotels, each with its name, city, and nightly price.
            Be concise and do not invent hotels that were not returned to you.
            """;

  /** Keeps multi-turn conversations bounded so context does not grow without limit. */
  private static final int MEMORY_WINDOW_MESSAGES = 20;

  private final ChatClient chatClient;
  private final VectorStore vectorStore;
  private final HotelTools hotelTools;
  private final ChatMemory chatMemory;
  private final AiLabProperties properties;

  public HotelAssistantService(
      ChatClient.Builder chatClientBuilder,
      VectorStore vectorStore,
      HotelTools hotelTools,
      AiLabProperties properties) {
    this.vectorStore = vectorStore;
    this.hotelTools = hotelTools;
    this.properties = properties;
    this.chatMemory =
        MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(MEMORY_WINDOW_MESSAGES)
            .build();
    this.chatClient =
        chatClientBuilder.defaultSystem(SYSTEM_PROMPT).defaultTools(hotelTools).build();
  }

  public AssistantResult ask(String question, String conversationId) {
    properties.requireApiKey();
    String userQuestion =
        (question == null || question.isBlank())
            ? "Find hotels near the beach under $100"
            : question;
    String conversation =
        (conversationId == null || conversationId.isBlank()) ? "voyage-ai-lab" : conversationId;

    QuestionAnswerAdvisor ragAdvisor =
        QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(
                SearchRequest.builder()
                    .topK(properties.topK())
                    .similarityThreshold(properties.similarityThreshold())
                    .build())
            .build();

    MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

    // Cleared up front so the trace only reflects this request.
    hotelTools.resetInvocations();

    long startedAt = System.currentTimeMillis();
    ChatClientResponse response =
        chatClient
            .prompt()
            .user(userQuestion)
            .advisors(ragAdvisor, memoryAdvisor)
            .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversation))
            .call()
            .chatClientResponse();
    long tookMs = System.currentTimeMillis() - startedAt;

    List<HotelTools.ToolInvocation> toolCalls = hotelTools.drainInvocations();

    return new AssistantResult(
        userQuestion,
        answerText(response),
        conversation,
        retrievedDocuments(response),
        toolCalls,
        AiPlaygroundService.TokenUsage.from(response.chatResponse()),
        tookMs,
        toolCalls.isEmpty()
            ? "The model answered from retrieved context alone this time. Ask about a budget or "
                + "specific dates to force a tool call."
            : "Retrieval found candidates by meaning; the tool calls checked them against the real "
                + "database. That division of labour is the whole design.",
        "Ask a follow-up like 'which of those has a pool?' using the same conversation id — chat memory "
            + "keeps the earlier turns in context, so 'those' still resolves.");
  }

  /** Wipes a conversation so the lab can be re-run from a clean slate. */
  public ClearResult clearConversation(String conversationId) {
    String conversation =
        (conversationId == null || conversationId.isBlank()) ? "voyage-ai-lab" : conversationId;
    chatMemory.clear(conversation);
    return new ClearResult(
        conversation, "Conversation history cleared. The next question starts fresh.");
  }

  @SuppressWarnings("unchecked")
  private static List<VectorSearchService.Match> retrievedDocuments(ChatClientResponse response) {
    Object documents = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
    if (documents instanceof List<?> list) {
      return ((List<Document>) list)
          .stream()
              .map(
                  document ->
                      new VectorSearchService.Match(
                          document.getId(),
                          document.getMetadata().get("name"),
                          document.getMetadata().get("city"),
                          document.getMetadata().get("pricePerNight"),
                          document.getScore(),
                          document.getText()))
              .toList();
    }
    return List.of();
  }

  private static String answerText(ChatClientResponse response) {
    if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
      return "";
    }
    return response.chatResponse().getResult().getOutput().getText();
  }

  public record AssistantResult(
      String question,
      String answer,
      String conversationId,
      List<VectorSearchService.Match> retrievedDocuments,
      List<HotelTools.ToolInvocation> toolCalls,
      AiPlaygroundService.TokenUsage usage,
      long tookMs,
      String observation,
      String tip) {}

  public record ClearResult(String conversationId, String message) {}
}
