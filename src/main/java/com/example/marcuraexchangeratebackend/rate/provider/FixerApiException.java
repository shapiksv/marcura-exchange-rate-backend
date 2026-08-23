package com.example.marcuraexchangeratebackend.rate.provider;

/**
 * Exception thrown when Fixer.io returns success=false.
 * This represents a business-level error from the Fixer API (invalid key, quota exceeded, etc.).
 */
public class FixerApiException extends RuntimeException {
    private final Integer errorCode;
    private final String errorType;

    public FixerApiException(String message, Integer errorCode, String errorType) {
        super(message);
        this.errorCode = errorCode;
        this.errorType = errorType;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public String getErrorType() {
        return errorType;
    }

    @Override
    public String toString() {
        return String.format("FixerApiException[code=%d, type=%s, message=%s]",
                errorCode, errorType, getMessage());
    }
}
