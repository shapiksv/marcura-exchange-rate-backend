package com.example.marcuraexchangeratebackend.currency.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response containing list of available currencies.
 * <p>
 * Currencies are extracted from the most recent exchange rate snapshot.
 */
@Schema(description = "List of available currencies")
public record CurrenciesResponse(
        @Schema(description = "List of currency codes (ISO 4217-like)", example = "[\"EUR\", \"USD\", \"PLN\"]")
        List<String> currencies
) {
}
