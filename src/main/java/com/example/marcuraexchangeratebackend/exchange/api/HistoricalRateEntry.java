package com.example.marcuraexchangeratebackend.exchange.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Single historical rate entry for a specific date.
 *
 * Contains both raw (cross) rate and spread-adjusted rate.
 * The frontend table shows "raw exchange rates" while the calculator uses adjusted rates.
 */
@Schema(description = "Exchange rate entry for a specific date")
public record HistoricalRateEntry(
        @Schema(description = "Date of this rate", example = "2024-02-01")
        LocalDate date,

        @Schema(description = "Raw cross rate (toRate / fromRate) without spread adjustment", example = "0.861234")
        BigDecimal rawRate,

        @Schema(description = "Spread-adjusted rate (used by calculator)", example = "0.837543")
        BigDecimal adjustedRate
) {
}
