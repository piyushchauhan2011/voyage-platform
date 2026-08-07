package com.voyage.app.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Phase 8 knobs, read from the {@code application.ai.*} namespace to match the convention the other
 * phases use ({@code application.kafka.*}, {@code application.redis.*}).
 */
@Component
public class AiLabProperties {

  /** Sentinel written into application.yml so the app boots without a Gemini key. */
  static final String UNCONFIGURED_KEY = "not-configured";

  private final String apiKey;
  private final String chatModel;
  private final String embeddingModel;
  private final int topK;
  private final double similarityThreshold;

  public AiLabProperties(
      @Value("${spring.ai.google.genai.api-key:}") String apiKey,
      @Value("${spring.ai.google.genai.chat.model:gemini-2.5-flash}") String chatModel,
      @Value("${spring.ai.google.genai.embedding.text.model:text-embedding-004}")
          String embeddingModel,
      @Value("${application.ai.top-k:4}") int topK,
      @Value("${application.ai.similarity-threshold:0.5}") double similarityThreshold) {
    this.apiKey = apiKey;
    this.chatModel = chatModel;
    this.embeddingModel = embeddingModel;
    this.topK = topK;
    this.similarityThreshold = similarityThreshold;
  }

  /**
   * The app is allowed to start without a Gemini key so the Kafka/Redis/JPA phases keep working.
   * Every AI endpoint checks this first and fails with a readable message rather than letting a 401
   * from Google surface as a stack trace.
   */
  public boolean isApiKeyConfigured() {
    return apiKey != null && !apiKey.isBlank() && !UNCONFIGURED_KEY.equals(apiKey);
  }

  public void requireApiKey() {
    if (!isApiKeyConfigured()) {
      throw new AiNotConfiguredException(
          "No Gemini API key found. Create one at https://aistudio.google.com/apikey, "
              + "then export GEMINI_API_KEY=... and restart the app.");
    }
  }

  public String chatModel() {
    return chatModel;
  }

  public String embeddingModel() {
    return embeddingModel;
  }

  public int topK() {
    return topK;
  }

  public double similarityThreshold() {
    return similarityThreshold;
  }
}
