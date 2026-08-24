package com.example.marcuraexchangeratebackend.exchange.api;

import com.example.marcuraexchangeratebackend.exchange.application.ExchangeApplicationService;
import com.example.marcuraexchangeratebackend.exchange.application.ExchangeResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST controller for exchange rate calculations.
 *
 * Provides the main exchange API endpoint.
 */
@RestController
@RequestMapping("/api/v1/exchange")
@Tag(name = "Exchange", description = "Exchange rate calculation API")
public class ExchangeController {

    private final ExchangeApplicationService exchangeService;

    public ExchangeController(ExchangeApplicationService exchangeService) {
        this.exchangeService = exchangeService;
    }

    /**
     * Calculate the exchange rate between two currencies.
     *
     * @param from the source currency code (3 uppercase letters, required)
     * @param to   the target currency code (3 uppercase letters, required)
     * @param date the rate date (optional, defaults to latest available)
     * @return the exchange response with adjusted rate and usage counts
     */
    @GetMapping
    @Operation(
            summary = "Calculate exchange rate",
            description = "Calculate the spread-adjusted exchange rate between two currencies. " +
                    "If date is omitted, uses the latest available rate. " +
                    "Increments usage counters for both currencies.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Exchange rate calculated successfully",
                            content = @Content(schema = @Schema(implementation = ExchangeResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request parameters (invalid currency code format)",
                            content = @Content
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Required rates not available for the requested date or currencies",
                            content = @Content
                    )
            }
    )
    public ResponseEntity<ExchangeResponse> calculateExchange(
            @Parameter(description = "Source currency code (3 uppercase letters)", required = true, example = "EUR")
            @RequestParam
            @NotBlank(message = "from currency is required")
            @Pattern(regexp = "^[A-Z]{3}$", message = "from currency must be 3 uppercase letters")
            String from,

            @Parameter(description = "Target currency code (3 uppercase letters)", required = true, example = "PLN")
            @RequestParam
            @NotBlank(message = "to currency is required")
            @Pattern(regexp = "^[A-Z]{3}$", message = "to currency must be 3 uppercase letters")
            String to,

            @Parameter(description = "Rate date (optional, format: yyyy-MM-dd). If omitted, uses latest available.", example = "2024-03-15")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        ExchangeResult result = exchangeService.calculateExchange(from, to, date);

        ExchangeResponse response = new ExchangeResponse(
                result.from(),
                result.to(),
                result.exchange(),
                result.date(),
                result.fromQueryCount(),
                result.toQueryCount()
        );

        return ResponseEntity.ok(response);
    }
}
