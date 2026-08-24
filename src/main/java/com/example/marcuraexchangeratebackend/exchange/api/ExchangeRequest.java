package com.example.marcuraexchangeratebackend.exchange.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * Request DTO for exchange rate calculation.
 *
 * @param from the source currency code (required, 3 uppercase letters)
 * @param to   the target currency code (required, 3 uppercase letters)
 * @param date the rate date (optional, defaults to latest available)
 */
public record ExchangeRequest(
        @NotBlank(message = "from currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "from currency must be 3 uppercase letters")
        String from,

        @NotBlank(message = "to currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "to currency must be 3 uppercase letters")
        String to,

        LocalDate date
) {
}
