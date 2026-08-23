package com.example.marcuraexchangeratebackend.rate.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Domain model representing a snapshot of exchange rates from Fixer.io.
 * Immutable record that captures the base currency, rate date, and all rates for a specific point in time.
 * <p>
 * The snapshot is normalized so the base currency is always present in the rates map with value 1.0.
 * All currency codes are normalized to uppercase.
 * All rates are validated (non-null, positive).
 */
public record RateSnapshot(
        LocalDate rateDate,
        String baseCurrency,
        Map<String, BigDecimal> rates
) {
    public RateSnapshot {
        if (rateDate == null) {
            throw new IllegalArgumentException("Rate date cannot be null");
        }
        if (baseCurrency == null || baseCurrency.isBlank()) {
            throw new IllegalArgumentException("Base currency cannot be null or blank");
        }

        // Normalize base currency to uppercase
        baseCurrency = baseCurrency.toUpperCase(Locale.ROOT);

        if (!isValidCurrencyCode(baseCurrency)) {
            throw new IllegalArgumentException("Invalid base currency format: " + baseCurrency);
        }

        if (rates == null || rates.isEmpty()) {
            throw new IllegalArgumentException("Rates map cannot be null or empty");
        }

        // Normalize and validate rates
        Map<String, BigDecimal> normalizedRates = new HashMap<>();

        for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
            String currencyCode = entry.getKey();
            BigDecimal rate = entry.getValue();

            // Validate currency code
            if (currencyCode == null || currencyCode.isBlank()) {
                throw new IllegalArgumentException("Currency code cannot be null or blank");
            }

            String normalizedCode = currencyCode.toUpperCase(Locale.ROOT);
            if (!isValidCurrencyCode(normalizedCode)) {
                throw new IllegalArgumentException("Invalid currency code format: " + currencyCode);
            }

            // Validate rate value
            if (rate == null) {
                throw new IllegalArgumentException("Rate value cannot be null for currency: " + normalizedCode);
            }
            if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Rate value must be positive for currency: " + normalizedCode + ", got: " + rate);
            }

            normalizedRates.put(normalizedCode, rate);
        }

        // Domain invariant: base currency ALWAYS has rate = 1
        // Explicitly overwrite to enforce this invariant even if Fixer supplies a different value
        normalizedRates.put(baseCurrency, BigDecimal.ONE);

        rates = Map.copyOf(normalizedRates);
    }

    /**
     * Validates currency code format (3 uppercase letters).
     */
    private static boolean isValidCurrencyCode(String code) {
        return code != null && code.matches("[A-Z]{3}");
    }
}
