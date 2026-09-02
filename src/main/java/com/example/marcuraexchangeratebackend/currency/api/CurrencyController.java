package com.example.marcuraexchangeratebackend.currency.api;

import com.example.marcuraexchangeratebackend.currency.application.CurrencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for currency-related operations.
 * <p>
 * Provides endpoints to retrieve available currencies from the latest exchange rate snapshot.
 */
@RestController
@RequestMapping("/api/v1/currencies")
@Tag(name = "Currencies", description = "Currency information endpoints")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    /**
     * Get list of available currencies from the most recent exchange rate snapshot.
     * <p>
     * Returns all unique currency codes (including base currencies) sorted alphabetically.
     * <p>
     * This endpoint does NOT increment usage counters.
     *
     * @return list of available currency codes
     */
    @GetMapping
    @Operation(
            summary = "Get available currencies",
            description = "Returns all unique currency codes from the most recent exchange rate snapshot. " +
                    "The list includes both target currencies and base currencies, sorted alphabetically. " +
                    "Does NOT increment usage counters.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "List of available currencies retrieved successfully",
                            content = @Content(schema = @Schema(implementation = CurrenciesResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "No exchange rates available in the database"
                    )
            }
    )
    public ResponseEntity<CurrenciesResponse> getAvailableCurrencies() {
        List<String> currencies = currencyService.getAvailableCurrencies();
        
        CurrenciesResponse response = new CurrenciesResponse(currencies);
        
        return ResponseEntity.ok(response);
    }
}
