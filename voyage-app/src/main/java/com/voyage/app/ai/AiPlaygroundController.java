package com.voyage.app.ai;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 8 playground — one endpoint per rung of the AI ladder, in the order you should try them.
 *
 * <p>Mirrors the RabbitMQ/JPA/Postgres playgrounds: thin controller, logic in the services, local
 * exception handlers so failures come back as readable JSON.
 */
@RestController
@RequestMapping("/api/ai/playground")
@ConditionalOnProperty(name = "application.ai.enabled", havingValue = "true", matchIfMissing = true)
public class AiPlaygroundController {

  private final AiLabProperties properties;
  private final HotelCatalogSeeder hotelCatalogSeeder;
  private final AiPlaygroundService aiPlaygroundService;
  private final HotelDocumentIngestor hotelDocumentIngestor;
  private final VectorSearchService vectorSearchService;
  private final HotelAssistantService hotelAssistantService;

  public AiPlaygroundController(
      AiLabProperties properties,
      HotelCatalogSeeder hotelCatalogSeeder,
      AiPlaygroundService aiPlaygroundService,
      HotelDocumentIngestor hotelDocumentIngestor,
      VectorSearchService vectorSearchService,
      HotelAssistantService hotelAssistantService) {
    this.properties = properties;
    this.hotelCatalogSeeder = hotelCatalogSeeder;
    this.aiPlaygroundService = aiPlaygroundService;
    this.hotelDocumentIngestor = hotelDocumentIngestor;
    this.vectorSearchService = vectorSearchService;
    this.hotelAssistantService = hotelAssistantService;
  }

  /** Startup check: is a Gemini key present, and which models are wired up. */
  @GetMapping("/status")
  public StatusResult status() {
    return new StatusResult(
        properties.isApiKeyConfigured(),
        properties.chatModel(),
        properties.embeddingModel(),
        properties.topK(),
        properties.similarityThreshold(),
        properties.isApiKeyConfigured()
            ? "Gemini key detected. Start with Seed catalog, then Ingest."
            : "No Gemini API key. Export GEMINI_API_KEY and restart — every other rung will fail without it.");
  }

  // --- Rung 0: data ---

  @PostMapping("/seed-catalog")
  public HotelCatalogSeeder.SeedResult seedCatalog() {
    return hotelCatalogSeeder.seed();
  }

  // --- Rung 1: chat ---

  @PostMapping("/chat")
  public AiPlaygroundService.ChatResult chat(@RequestBody(required = false) ChatRequest request) {
    return aiPlaygroundService.chat(
        request == null ? null : request.message(),
        request == null ? null : request.systemPrompt());
  }

  @PostMapping("/prompt-template")
  public AiPlaygroundService.PromptTemplateResult promptTemplate(
      @RequestBody(required = false) PromptTemplateRequest request) {
    return aiPlaygroundService.promptTemplate(
        request == null ? null : request.city(),
        request == null ? null : request.budget(),
        request == null ? null : request.vibe());
  }

  // --- Rung 2: embeddings ---

  @PostMapping("/embed")
  public AiPlaygroundService.EmbeddingResult embed(
      @RequestBody(required = false) EmbedRequest request) {
    return aiPlaygroundService.embed(
        request == null ? null : request.first(), request == null ? null : request.second());
  }

  // --- Rung 3: vector store ---

  @PostMapping("/ingest")
  public HotelDocumentIngestor.IngestResult ingest() {
    return hotelDocumentIngestor.ingest();
  }

  // --- Rung 4: similarity search ---

  @PostMapping("/search")
  public VectorSearchService.SearchResult search(
      @RequestBody(required = false) SearchRequestBody request) {
    return vectorSearchService.search(
        request == null ? null : request.query(),
        request == null ? null : request.topK(),
        request == null ? null : request.filterExpression());
  }

  // --- Rung 5: RAG ---

  @PostMapping("/rag")
  public VectorSearchService.RagResult rag(@RequestBody(required = false) RagRequest request) {
    return vectorSearchService.rag(
        request == null ? null : request.question(),
        request == null ? null : request.topK(),
        request == null ? null : request.filterExpression());
  }

  // --- Rung 6: tools ---

  /** Shows the tool contract the model sees — name, description, and parameters. */
  @GetMapping("/tools")
  public ToolCatalogResult tools() {
    List<ToolDescription> tools =
        List.of(
            new ToolDescription(
                "searchHotels",
                "Search hotels by city and/or nightly price range",
                List.of("city (optional)", "minPrice (optional)", "maxPrice (optional)")),
            new ToolDescription(
                "getHotelDetails", "Fetch one hotel's full details by id", List.of("hotelId")),
            new ToolDescription(
                "checkAvailability",
                "Check room availability for a date range",
                List.of("hotelId", "checkInDate", "checkOutDate", "roomType (optional)")));
    return new ToolCatalogResult(
        tools,
        "Spring AI turns each @Tool method into a JSON schema and sends it with the prompt. "
            + "The model replies with a tool name and arguments; Spring AI runs the Java method.",
        "The description text is the model's only guide to when a tool applies — it is part of the "
            + "contract, not a comment.");
  }

  // --- Rung 7: the agent ---

  @PostMapping("/assistant")
  public HotelAssistantService.AssistantResult assistant(
      @RequestBody(required = false) AssistantRequest request) {
    return hotelAssistantService.ask(
        request == null ? null : request.question(),
        request == null ? null : request.conversationId());
  }

  @PostMapping("/assistant/clear")
  public HotelAssistantService.ClearResult clearConversation(
      @RequestBody(required = false) AssistantRequest request) {
    return hotelAssistantService.clearConversation(
        request == null ? null : request.conversationId());
  }

  // ------------------------------------------------------------------
  // Error handling — same shape as the other lab playgrounds
  // ------------------------------------------------------------------

  @ExceptionHandler(AiNotConfiguredException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public Map<String, String> handleNotConfigured(AiNotConfiguredException exception) {
    return Map.of("error", exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleBadRequest(IllegalArgumentException exception) {
    return Map.of("error", exception.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> handleConflict(IllegalStateException exception) {
    return Map.of("error", exception.getMessage());
  }

  // ------------------------------------------------------------------
  // Request/response payloads
  // ------------------------------------------------------------------

  public record ChatRequest(String message, String systemPrompt) {}

  public record PromptTemplateRequest(String city, Double budget, String vibe) {}

  public record EmbedRequest(String first, String second) {}

  public record SearchRequestBody(String query, Integer topK, String filterExpression) {}

  public record RagRequest(String question, Integer topK, String filterExpression) {}

  public record AssistantRequest(String question, String conversationId) {}

  public record ToolDescription(String name, String description, List<String> parameters) {}

  public record ToolCatalogResult(List<ToolDescription> tools, String observation, String tip) {}

  public record StatusResult(
      boolean apiKeyConfigured,
      String chatModel,
      String embeddingModel,
      int topK,
      double similarityThreshold,
      String observation) {}
}
