package com.example.marcuraexchangeratebackend.insight.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Response DTO for AI-generated trend insight.
 * <p>
 * Contains the currency pair, date range, and generated insight text.
 */
@Schema(description = "AI-generated trend insight for a currency pair within a date range")
public record TrendInsightResponse(
        @Schema(description = "Source currency code", example = "EUR")
        String from,

        @Schema(description = "Target currency code", example = "GBP")
        String to,

        @Schema(description = "Start of date range (inclusive)", example = "2024-02-01")
        LocalDate fromDate,

        @Schema(description = "End of date range (inclusive)", example = "2024-03-01")
        LocalDate toDate,

        @Schema(description = "AI-generated concise trend insight based on actual historical data",
                example = "EUR/GBP declined by approximately 1.8% over the selected period, with the strongest downward movement occurring near the end of February.")
        String insight
) {
}
