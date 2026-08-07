package com.voyage.app.ai;

/**
 * Raised when an AI endpoint is called but no Gemini API key is configured.
 * Mapped to 503 so it reads as "this service is not set up" rather than "you sent a bad request".
 */
public class AiNotConfiguredException extends RuntimeException {

    public AiNotConfiguredException(String message) {
        super(message);
    }
}
