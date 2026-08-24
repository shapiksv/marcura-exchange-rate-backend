package com.example.marcuraexchangeratebackend.insight.domain;

/**
 * Exception thrown when the AI provider (Ollama, OpenAI, etc.) is unavailable or fails.
 * <p>
 * Used to separate AI infrastructure failures from application/domain logic.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
