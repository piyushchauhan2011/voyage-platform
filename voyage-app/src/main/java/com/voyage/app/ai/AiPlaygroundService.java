package com.voyage.app.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Rungs 1 and 2 of the Phase 8 ladder: plain chat, then embeddings.
 *
 * Everything here is deliberately transparent — each result carries the prompt that was
 * actually sent and an observation explaining what to notice, because the interesting part
 * of learning AI plumbing is seeing the intermediate state, not just the final answer.
 */
@Service
@ConditionalOnProperty(name = "application.ai.enabled", havingValue = "true", matchIfMissing = true)
public class AiPlaygroundService {

    /**
     * Only the leading slice of each vector is returned to the browser. A full
     * text-embedding-004 vector is 768 floats, which is noise on screen.
     */
    private static final int VECTOR_PREVIEW_LENGTH = 8;

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final AiLabProperties properties;

    public AiPlaygroundService(ChatClient.Builder chatClientBuilder,
                               EmbeddingModel embeddingModel,
                               AiLabProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Rung 1 — chat
    // ------------------------------------------------------------------

    /**
     * The smallest useful Spring AI call: a system message setting the persona and a user
     * message. No retrieval, no tools — so any hotel it names is invented. That failure is
     * the point, and it is what the RAG rung fixes.
     */
    public ChatResult chat(String message, String systemPrompt) {
        properties.requireApiKey();
        String userMessage = (message == null || message.isBlank())
                ? "What should I consider when booking a hotel for a beach holiday?"
                : message;
        String system = (systemPrompt == null || systemPrompt.isBlank())
                ? "You are a concise travel assistant for the Voyage booking platform."
                : systemPrompt;

        long startedAt = System.currentTimeMillis();
        ChatResponse response = chatClient.prompt()
                .system(system)
                .user(userMessage)
                .call()
                .chatResponse();
        long tookMs = System.currentTimeMillis() - startedAt;

        return new ChatResult(
                properties.chatModel(),
                system,
                userMessage,
                text(response),
                TokenUsage.from(response),
                tookMs,
                "The model answered from training data alone. Ask it for a specific Voyage hotel and "
                        + "it will either refuse or invent one, because it has never seen your database.",
                "This is the baseline. Compare its answer to the /rag and /assistant rungs."
        );
    }

    /**
     * Prompt templates are string interpolation with guardrails: placeholders are declared
     * up front, so the caller supplies data rather than concatenating raw text into the prompt.
     */
    public PromptTemplateResult promptTemplate(String city, Double budget, String vibe) {
        properties.requireApiKey();
        String template = """
                Suggest what kind of hotel a traveller should look for.
                City: {city}
                Nightly budget: {budget} USD
                Preferred vibe: {vibe}
                Answer in at most three sentences.
                """;

        Map<String, Object> variables = Map.of(
                "city", city == null || city.isBlank() ? "Lisbon" : city,
                "budget", budget == null ? 100.0 : budget,
                "vibe", vibe == null || vibe.isBlank() ? "close to the beach" : vibe);

        String rendered = PromptTemplate.builder()
                .template(template)
                .variables(variables)
                .build()
                .render();

        long startedAt = System.currentTimeMillis();
        ChatResponse response = chatClient.prompt()
                .user(rendered)
                .call()
                .chatResponse();
        long tookMs = System.currentTimeMillis() - startedAt;

        return new PromptTemplateResult(
                template.strip(),
                variables,
                rendered.strip(),
                text(response),
                TokenUsage.from(response),
                tookMs,
                "The template is the reusable part; the variables are the request-specific part.",
                "Keeping them separate is what lets you review and version a prompt like code."
        );
    }

    // ------------------------------------------------------------------
    // Rung 2 — embeddings
    // ------------------------------------------------------------------

    /**
     * Embeds two texts and scores them against each other.
     *
     * Cosine similarity is the entire trick behind semantic search: text with related meaning
     * lands in a similar direction in vector space, even with no shared words.
     */
    public EmbeddingResult embed(String first, String second) {
        properties.requireApiKey();
        String left = (first == null || first.isBlank()) ? "a quiet room a short walk from the sand" : first;
        String right = (second == null || second.isBlank()) ? "beachfront hotel near the sea" : second;

        long startedAt = System.currentTimeMillis();
        List<float[]> vectors = embeddingModel.embed(List.of(left, right));
        long tookMs = System.currentTimeMillis() - startedAt;

        float[] leftVector = vectors.get(0);
        float[] rightVector = vectors.get(1);

        return new EmbeddingResult(
                properties.embeddingModel(),
                leftVector.length,
                new EmbeddedText(left, preview(leftVector)),
                new EmbeddedText(right, preview(rightVector)),
                cosineSimilarity(leftVector, rightVector),
                cosineSimilarity(leftVector, embeddingModel.embed("annual report on quarterly tax filings")),
                tookMs,
                "The two travel phrases score high against each other despite sharing almost no words, "
                        + "while the unrelated sentence scores far lower. Meaning, not keywords.",
                "Every vector here is " + leftVector.length + " floats — which is exactly why "
                        + "spring.ai.vectorstore.pgvector.dimensions has to be set to match."
        );
    }

    /**
     * Standard cosine similarity. Written out rather than pulled from a library because
     * seeing the formula is half the lesson: it is just a normalised dot product.
     */
    static double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("Vectors must have the same length to be compared");
        }
        double dotProduct = 0.0;
        double leftMagnitude = 0.0;
        double rightMagnitude = 0.0;
        for (int i = 0; i < left.length; i++) {
            dotProduct += (double) left[i] * right[i];
            leftMagnitude += (double) left[i] * left[i];
            rightMagnitude += (double) right[i] * right[i];
        }
        if (leftMagnitude == 0.0 || rightMagnitude == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private static List<Float> preview(float[] vector) {
        int length = Math.min(VECTOR_PREVIEW_LENGTH, vector.length);
        List<Float> preview = new java.util.ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            preview.add(vector[i]);
        }
        return preview;
    }

    private static String text(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    // ------------------------------------------------------------------
    // Result types
    // ------------------------------------------------------------------

    public record ChatResult(
            String model,
            String systemPrompt,
            String userMessage,
            String answer,
            TokenUsage usage,
            long tookMs,
            String observation,
            String tip
    ) {
    }

    public record PromptTemplateResult(
            String template,
            Map<String, Object> variables,
            String renderedPrompt,
            String answer,
            TokenUsage usage,
            long tookMs,
            String observation,
            String tip
    ) {
    }

    public record EmbeddedText(String text, List<Float> vectorPreview) {
    }

    public record EmbeddingResult(
            String model,
            int dimensions,
            EmbeddedText first,
            EmbeddedText second,
            double similarity,
            double similarityToUnrelatedText,
            long tookMs,
            String observation,
            String tip
    ) {
    }

    public record TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {

        static TokenUsage from(ChatResponse response) {
            if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
                return new TokenUsage(null, null, null);
            }
            var usage = response.getMetadata().getUsage();
            return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        }
    }
}
