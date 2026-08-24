package com.example.marcuraexchangeratebackend.exchange.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Response for historical exchange rate query.
 *
 * Contains the request parameters and a list of historical rates for the date range.
 */
@Schema(description = "Historical exchange rate data for a currency pair over a date range")
public record ExchangeHistoryResponse(
        @Schema(description = "Source currency code", example = "EUR")
        String from,

        @Schema(description = "Target currency code", example = "GBP")
        String to,

        @Schema(description = "Start date of the range", example = "2024-02-01")
        LocalDate fromDate,

        @Schema(description = "End date of the range", example = "2024-03-01")
        LocalDate toDate,

        @Schema(description = "List of historical rates, one per available date")
        List<HistoricalRateEntry> rates
) {
}
