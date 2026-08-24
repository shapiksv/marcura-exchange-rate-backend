package com.example.marcuraexchangeratebackend.exchange.api;

import com.example.marcuraexchangeratebackend.exchange.application.ExchangeHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for historical exchange rate data.
 * <p>
 * Provides read-only access to historical rate data without affecting usage counters.
 */
@RestController
@RequestMapping("/api/v1/exchange/history")
@Tag(name = "Exchange History", description = "Historical exchange rate data API")
public class ExchangeHistoryController {

    private final ExchangeHistoryService historyService;

    public ExchangeHistoryController(ExchangeHistoryService historyService) {
        this.historyService = historyService;
    }

    /**
     * Get historical exchange rates for a currency pair within a date range.
     *
     * @param from     the source currency code (3 uppercase letters, required)
     * @param to       the target currency code (3 uppercase letters, required)
     * @param fromDate the start date of the range (inclusive, required)
     * @param toDate   the end date of the range (inclusive, required)
     * @return historical rate data for all available dates in the range
     */
    @GetMapping
    @Operation(
            summary = "Get historical exchange rates",
            description = "Returns historical exchange rate data for a currency pair within a date range. " +
                    "Provides both raw (toRate/fromRate) and spread-adjusted rates. " +
                    "Missing dates are omitted (not fabricated). " +
                    "Does NOT increment usage counters.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Historical data retrieved successfully (may be empty if no data available)",
                            content = @Content(schema = @Schema(implementation = ExchangeHistoryResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request parameters (invalid currency format or fromDate > toDate)",
                            content = @Content
                    )
            }
    )
    public ResponseEntity<ExchangeHistoryResponse> getHistoricalRates(
            @Parameter(description = "Source currency code (3 uppercase letters)", required = true, example = "EUR")
            @RequestParam
            @NotBlank(message = "from currency is required")
            @Pattern(regexp = "^[A-Z]{3}$", message = "from currency must be 3 uppercase letters")
            String from,

            @Parameter(description = "Target currency code (3 uppercase letters)", required = true, example = "GBP")
            @RequestParam
            @NotBlank(message = "to currency is required")
            @Pattern(regexp = "^[A-Z]{3}$", message = "to currency must be 3 uppercase letters")
            String to,

            @Parameter(description = "Start date of the range (inclusive, format: yyyy-MM-dd)", required = true, example = "2024-02-01")
            @RequestParam
            @NotNull(message = "fromDate is required")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @Parameter(description = "End date of the range (inclusive, format: yyyy-MM-dd)", required = true, example = "2024-03-01")
            @RequestParam
            @NotNull(message = "toDate is required")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        List<HistoricalRateEntry> rates = historyService.getHistoricalRates(from, to, fromDate, toDate);

        ExchangeHistoryResponse response = new ExchangeHistoryResponse(
                from,
                to,
                fromDate,
                toDate,
                rates
        );

        return ResponseEntity.ok(response);
    }
}
