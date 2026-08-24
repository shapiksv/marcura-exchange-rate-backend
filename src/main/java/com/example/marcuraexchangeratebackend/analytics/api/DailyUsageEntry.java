package com.example.marcuraexchangeratebackend.analytics.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Daily usage record for a specific currency on a specific date.
 * <p>
 * Used for analytics dashboard to show usage patterns over time.
 */
@Schema(description = "Currency usage for a specific date")
public record DailyUsageEntry(
        @Schema(description = "Query date", example = "2024-03-15")
        LocalDate date,

        @Schema(description = "Currency code", example = "EUR")
        String currency,

        @Schema(description = "Number of queries on this date", example = "12")
        Long count
) {
}
