package com.example.marcuraexchangeratebackend.common.error;

/**
 * Exception thrown when request validation fails (invalid currency code, etc.).
 * <p>
 * This exception should result in HTTP 400 (Bad Request).
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
