package com.example.marcuraexchangeratebackend.rate.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain tests for RateSnapshot normalization and validation.
 * Tests:
 * - Base currency always normalized to 1 (domain invariant)
 * - Currency code normalization (uppercase)
 * - Currency code validation (3-letter format)
 * - Rate value validation (positive, non-null)
 * - Date validation (non-null)
 */
class RateSnapshotTest {

    @Test
    void rateSnapshot_baseCurrencyNotInRates_addedWithValueOne() {
        // Given - Fixer response WITHOUT base currency in rates map
        RateSnapshot snapshot = new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                "EUR",
                Map.of(
                        "USD", new BigDecimal("1.08360"),
                        "PLN", new BigDecimal("4.56734")
                )
        );

        // Then - base currency EUR present with rate=1
        assertThat(snapshot.rates()).containsEntry("EUR", BigDecimal.ONE);
        assertThat(snapshot.rates()).hasSize(3); // EUR, USD, PLN
    }

    @Test
    void rateSnapshot_baseCurrencyAlreadyInRates_overwrittenToOne() {
        // Given - Fixer incorrectly supplies base currency with value != 1
        RateSnapshot snapshot = new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                "EUR",
                Map.of(
                        "EUR", new BigDecimal("0.99"), // ← incorrect value from Fixer
                        "USD", new BigDecimal("1.08360")
                )
        );

        // Then - base currency EUR OVERWRITTEN to exactly 1
        assertThat(snapshot.rates()).containsEntry("EUR", BigDecimal.ONE);
        assertThat(snapshot.rates().get("EUR")).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(snapshot.rates()).hasSize(2); // EUR, USD
    }

    @Test
    void rateSnapshot_currencyCodeNormalization_upperCase() {
        // Given - lowercase currency codes
        RateSnapshot snapshot = new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                "eur", // lowercase base
                Map.of("usd", new BigDecimal("1.08360")) // lowercase currency
        );

        // Then - normalized to uppercase
        assertThat(snapshot.baseCurrency()).isEqualTo("EUR");
        assertThat(snapshot.rates()).containsKey("EUR");
        assertThat(snapshot.rates()).containsKey("USD");
        assertThat(snapshot.rates()).doesNotContainKey("eur");
        assertThat(snapshot.rates()).doesNotContainKey("usd");
    }

    @Test
    void rateSnapshot_nullDate_throwsException() {
        assertThatThrownBy(() -> new RateSnapshot(
                null, // null date
                "EUR",
                Map.of("USD", new BigDecimal("1.08360"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rate date cannot be null");
    }

    @Test
    void rateSnapshot_nullBase_throwsException() {
        assertThatThrownBy(() -> new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                null, // null base
                Map.of("USD", new BigDecimal("1.08360"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base currency cannot be null");
    }

    @Test
    void rateSnapshot_blankBase_throwsException() {
        assertThatThrownBy(() -> new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                "  ", // blank base
                Map.of("USD", new BigDecimal("1.08360"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base currency cannot be null or blank");
    }

    @Test
    void rateSnapshot_invalidBaseCurrencyFormat_throwsException() {
        assertThatThrownBy(() -> new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                "EU", // only 2 letters
                Map.of("USD", new BigDecimal("1.08360"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid base currency format");
    }

    @Test
    void rateSnapshot_emptyRates_throwsException() {
        assertThatThrownBy(() -> new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                "EUR",
                Map.of() // empty rates
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rates map cannot be null or empty");
    }

    @Test
    void rateSnapshot_blankCurrencyCode_throwsException() {
        assertThatThrownBy(() -> new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                "EUR",
                Map.of("  ", new BigDecimal("1.08360")) // blank currency code
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency code cannot be null or blank");
    }

    @Test
    void rateSnapshot_invalidCurrencyCodeFormat_throwsException() {
        assertThatThrownBy(() -> new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                "EUR",
                Map.of("US", new BigDecimal("1.08360")) // only 2 letters
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid currency code format");
    }

    @Test
    void rateSnapshot_nullRateValue_throwsException() {
        // Cannot use Map.of() with null values - it throws NPE
        Map<String, BigDecimal> ratesWithNull = new HashMap<>();
        ratesWithNull.put("USD", null);
        
        assertThatThrownBy(() -> new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                "EUR",
                ratesWithNull // null rate value
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rate value cannot be null");
    }

    @Test
    void rateSnapshot_zeroRateValue_throwsException() {
        assertThatThrownBy(() -> new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                "EUR",
                Map.of("USD", BigDecimal.ZERO) // zero rate
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rate value must be positive");
    }

    @Test
    void rateSnapshot_negativeRateValue_throwsException() {
        assertThatThrownBy(() -> new RateSnapshot(
                LocalDate.of(2024, 3, 15),
                "EUR",
                Map.of("USD", new BigDecimal("-1.08360")) // negative rate
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rate value must be positive");
    }
}
