package com.voyage.app.ai;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Rungs 4 and 5 — raw similarity search, then the same search wired into a prompt as RAG.
 *
 * <p>Splitting these apart matters: search on its own shows you exactly which documents were
 * retrieved and how well they scored, so when the RAG answer is wrong you can tell whether
 * retrieval failed or the model ignored what it was given.
 */
@Service
@ConditionalOnProperty(name = "application.ai.enabled", havingValue = "true", matchIfMissing = true)
public class VectorSearchService {

  private static final String RAG_SYSTEM_PROMPT =
      """
            You are the Voyage hotel assistant.
            Answer only from the hotel context supplied to you.
            If the context does not contain a suitable hotel, say so plainly instead of guessing.
            Always name the hotels you recommend and give their nightly price.
            """;

  private final VectorStore vectorStore;
  private final ChatClient chatClient;
  private final AiLabProperties properties;

  public VectorSearchService(
      VectorStore vectorStore, ChatClient.Builder chatClientBuilder, AiLabProperties properties) {
    this.vectorStore = vectorStore;
    this.chatClient = chatClientBuilder.build();
    this.properties = properties;
  }

  /**
   * Pure retrieval — no model call beyond embedding the query.
   *
   * <p>The optional filter expression is where metadata earns its place: it is applied as a real
   * predicate against the JSONB column before ranking, so it excludes rather than merely
   * de-prioritises. For example {@code pricePerNight < 100}.
   */
  public SearchResult search(String query, Integer topK, String filterExpression) {
    properties.requireApiKey();
    String searchQuery = (query == null || query.isBlank()) ? "hotels near the beach" : query;
    int limit = topK == null || topK <= 0 ? properties.topK() : topK;

    SearchRequest.Builder request =
        SearchRequest.builder()
            .query(searchQuery)
            .topK(limit)
            .similarityThreshold(properties.similarityThreshold());
    if (filterExpression != null && !filterExpression.isBlank()) {
      request.filterExpression(filterExpression);
    }

    long startedAt = System.currentTimeMillis();
    List<Document> documents = vectorStore.similaritySearch(request.build());
    long tookMs = System.currentTimeMillis() - startedAt;

    List<Match> matches = toMatches(documents);
    return new SearchResult(
        searchQuery,
        limit,
        filterExpression,
        properties.similarityThreshold(),
        matches,
        tookMs,
        matches.isEmpty()
            ? "Nothing scored above the similarity threshold. Either the catalog was never "
                + "ingested, or the filter excluded every candidate."
            : "Ranked by meaning: hotels describing sand and surf surface for a beach query even "
                + "when neither the name nor the city contains the word 'beach'.",
        "Try the filter 'pricePerNight < 100' to see metadata narrow the candidates before ranking.");
  }

  /**
   * Retrieval plus generation. QuestionAnswerAdvisor runs the same similarity search, pastes the
   * results into the prompt as context, and hands the whole thing to Gemini.
   *
   * <p>The retrieved documents are pulled back out of the advisor's response context so the answer
   * can be checked against its own sources — the practical defence against a confident
   * hallucination.
   */
  public RagResult rag(String question, Integer topK, String filterExpression) {
    properties.requireApiKey();
    String userQuestion =
        (question == null || question.isBlank())
            ? "Find hotels near the beach under $100"
            : question;
    int limit = topK == null || topK <= 0 ? properties.topK() : topK;

    SearchRequest.Builder searchRequest =
        SearchRequest.builder().topK(limit).similarityThreshold(properties.similarityThreshold());
    if (filterExpression != null && !filterExpression.isBlank()) {
      searchRequest.filterExpression(filterExpression);
    }

    QuestionAnswerAdvisor advisor =
        QuestionAnswerAdvisor.builder(vectorStore).searchRequest(searchRequest.build()).build();

    long startedAt = System.currentTimeMillis();
    ChatClientResponse response =
        chatClient
            .prompt()
            .system(RAG_SYSTEM_PROMPT)
            .user(userQuestion)
            .advisors(advisor)
            .call()
            .chatClientResponse();
    long tookMs = System.currentTimeMillis() - startedAt;

    return new RagResult(
        userQuestion,
        answerText(response),
        retrievedDocuments(response),
        filterExpression,
        AiPlaygroundService.TokenUsage.from(response.chatResponse()),
        tookMs,
        "The answer now cites real Voyage hotels, because the retrieved rows were pasted into the "
            + "prompt before the model saw the question.",
        "Retrieval is still fuzzy. Ask for 'under $100' without a filter and the model may include a "
            + "$320 resort — a price limit is arithmetic, which is the job of the tool-calling rung.");
  }

  @SuppressWarnings("unchecked")
  private static List<Match> retrievedDocuments(ChatClientResponse response) {
    Object documents = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
    if (documents instanceof List<?> list) {
      return toMatches((List<Document>) list);
    }
    return List.of();
  }

  private static List<Match> toMatches(List<Document> documents) {
    if (documents == null) {
      return List.of();
    }
    return documents.stream()
        .map(
            document ->
                new Match(
                    document.getId(),
                    document.getMetadata().get("name"),
                    document.getMetadata().get("city"),
                    document.getMetadata().get("pricePerNight"),
                    document.getScore(),
                    document.getText()))
        .toList();
  }

  private static String answerText(ChatClientResponse response) {
    if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
      return "";
    }
    return response.chatResponse().getResult().getOutput().getText();
  }

  public record Match(
      String documentId,
      Object name,
      Object city,
      Object pricePerNight,
      Double score,
      String text) {}

  public record SearchResult(
      String query,
      int topK,
      String filterExpression,
      double similarityThreshold,
      List<Match> matches,
      long tookMs,
      String observation,
      String tip) {}

  public record RagResult(
      String question,
      String answer,
      List<Match> retrievedDocuments,
      String filterExpression,
      AiPlaygroundService.TokenUsage usage,
      long tookMs,
      String observation,
      String tip) {}
}
