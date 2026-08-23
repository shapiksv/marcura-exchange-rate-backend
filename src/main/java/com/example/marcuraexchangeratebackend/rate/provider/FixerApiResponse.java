package com.example.marcuraexchangeratebackend.rate.provider;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * DTO representing the JSON response from Fixer.io API.
 * Fixer returns:
 * {
 *   "success": true,
 *   "timestamp": 1519296206,
 *   "base": "EUR",
 *   "date": "2024-03-15",
 *   "rates": {
 *     "USD": 1.23456,
 *     "PLN": 4.56789,
 *     ...
 *   }
 * }
 */
record FixerApiResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("timestamp") Long timestamp,
        @JsonProperty("base") String base,
        @JsonProperty("date") LocalDate date,
        @JsonProperty("rates") Map<String, BigDecimal> rates,
        @JsonProperty("error") FixerErrorInfo error
) {
    /**
     * Fixer error information when success=false.
     */
    record FixerErrorInfo(
            @JsonProperty("code") Integer code,
            @JsonProperty("type") String type,
            @JsonProperty("info") String info
    ) {
    }
}
