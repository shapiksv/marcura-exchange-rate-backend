package com.example.marcuraexchangeratebackend.common.error;

/**
 * Exception thrown when requested exchange rate data is not available.
 * <p>
 * This exception should result in HTTP 404 (Not Found).
 */
public class RateNotFoundException extends RuntimeException {

    public RateNotFoundException(String message) {
        super(message);
    }

    public RateNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
