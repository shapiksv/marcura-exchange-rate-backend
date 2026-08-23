package com.example.marcuraexchangeratebackend.rate.provider;

/**
 * Exception thrown for transport errors, timeouts, malformed responses, or missing required fields.
 * This represents a technical failure in the provider integration.
 */
public class ExchangeRateProviderException extends RuntimeException {
    public ExchangeRateProviderException(String message) {
        super(message);
    }

    public ExchangeRateProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
