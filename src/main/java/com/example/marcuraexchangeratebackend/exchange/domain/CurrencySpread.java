package com.example.marcuraexchangeratebackend.exchange.domain;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Currency spread policy based on assessment requirements.
 *
 * Spread rules:
 * - Base currency from Fixer.io: 0.00%
 * - JPY, HKD, KRW: 3.25%
 * - MYR, INR, MXN: 4.50%
 * - RUB, CNY, ZAR: 6.00%
 * - All others: 2.75%
 */
public enum CurrencySpread {

    BASE_CURRENCY(BigDecimal.ZERO, "Base currency"),
    TIER_1(new BigDecimal("3.25"), Set.of("JPY", "HKD", "KRW")),
    TIER_2(new BigDecimal("4.50"), Set.of("MYR", "INR", "MXN")),
    TIER_3(new BigDecimal("6.00"), Set.of("RUB", "CNY", "ZAR")),
    DEFAULT(new BigDecimal("2.75"), "All other currencies");

    private final BigDecimal spreadPercentage;
    private final Set<String> currencies;
    private final String description;

    CurrencySpread(BigDecimal spreadPercentage, String description) {
        this.spreadPercentage = spreadPercentage;
        this.currencies = Set.of();
        this.description = description;
    }

    CurrencySpread(BigDecimal spreadPercentage, Set<String> currencies) {
        this.spreadPercentage = spreadPercentage;
        this.currencies = Set.copyOf(currencies);
        this.description = String.join(", ", currencies);
    }

    public BigDecimal getSpreadPercentage() {
        return spreadPercentage;
    }

    public Set<String> getCurrencies() {
        return currencies;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the spread percentage for a currency.
     *
     * @param currencyCode     the ISO currency code (e.g., "EUR", "USD")
     * @param isBaseCurrency   true if this is the base currency from Fixer.io
     * @return the spread percentage as BigDecimal
     */
    public static BigDecimal getSpreadFor(String currencyCode, boolean isBaseCurrency) {
        if (isBaseCurrency) {
            return BASE_CURRENCY.spreadPercentage;
        }

        for (CurrencySpread spread : values()) {
            if (spread.currencies.contains(currencyCode)) {
                return spread.spreadPercentage;
            }
        }

        return DEFAULT.spreadPercentage;
    }

    /**
     * Get the higher spread between two currencies.
     * This is used in the exchange calculation formula.
     *
     * @param fromCurrencyCode    the source currency code
     * @param toCurrencyCode      the target currency code
     * @param baseCurrencyCode    the base currency from Fixer.io
     * @return the maximum spread percentage
     */
    public static BigDecimal getHigherSpread(String fromCurrencyCode, String toCurrencyCode, String baseCurrencyCode) {
        BigDecimal fromSpread = getSpreadFor(fromCurrencyCode, fromCurrencyCode.equals(baseCurrencyCode));
        BigDecimal toSpread = getSpreadFor(toCurrencyCode, toCurrencyCode.equals(baseCurrencyCode));
        return fromSpread.max(toSpread);
    }
}
