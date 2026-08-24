package com.example.marcuraexchangeratebackend.analytics.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Usage summary for a single currency.
 * <p>
 * Contains aggregated total query count and last queried timestamp.
 */
@Schema(description = "Currency usage summary with total count and last query timestamp")
public record CurrencyUsageSummary(
        @Schema(description = "Currency code", example = "EUR")
        String currency,

        @Schema(description = "Total query count across all dates", example = "142")
        Long totalCount,

        @Schema(description = "Timestamp of most recent query", example = "2024-03-15T10:30:00Z")
        OffsetDateTime lastQueried
) {
}
