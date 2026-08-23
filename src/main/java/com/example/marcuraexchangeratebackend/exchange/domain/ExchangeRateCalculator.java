package com.example.marcuraexchangeratebackend.exchange.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Domain service for calculating spread-adjusted exchange rates.
 *
 * Formula (from assessment):
 * adjustedRate = (toRate / fromRate) * ((100 - max(toSpread, fromSpread)) / 100)
 *
 * Uses BigDecimal with explicit precision and rounding to avoid premature rounding.
 */
public class ExchangeRateCalculator {

    // MathContext for division operations: 34 digits precision with HALF_UP rounding
    private static final MathContext DIVISION_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);

    // Final result scale: 10 decimal places for exchange rates
    private static final int RESULT_SCALE = 10;

    // Constants for formula
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * Calculate the spread-adjusted exchange rate.
     *
     * @param fromRate          the rate value of the source currency (rate to base)
     * @param toRate            the rate value of the target currency (rate to base)
     * @param baseCurrencyCode  the base currency code from Fixer.io
     * @param fromCurrencyCode  the source currency code
     * @param toCurrencyCode    the target currency code
     * @return the adjusted exchange rate from fromCurrency to toCurrency
     * @throws IllegalArgumentException if any parameter is null or rates are not positive
     */
    public BigDecimal calculateAdjustedRate(
            BigDecimal fromRate,
            BigDecimal toRate,
            String baseCurrencyCode,
            String fromCurrencyCode,
            String toCurrencyCode
    ) {
        validateInputs(fromRate, toRate, baseCurrencyCode, fromCurrencyCode, toCurrencyCode);

        // Get the higher spread between the two currencies
        BigDecimal higherSpread = CurrencySpread.getHigherSpread(fromCurrencyCode, toCurrencyCode, baseCurrencyCode);

        // Calculate: (toRate / fromRate) * ((100 - higherSpread) / 100)
        BigDecimal crossRate = toRate.divide(fromRate, DIVISION_CONTEXT);
        BigDecimal spreadMultiplier = ONE_HUNDRED.subtract(higherSpread).divide(ONE_HUNDRED, DIVISION_CONTEXT);
        BigDecimal adjustedRate = crossRate.multiply(spreadMultiplier, DIVISION_CONTEXT);

        return adjustedRate.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate the raw cross rate without spread adjustment.
     * Useful for historical data or analytics.
     *
     * @param fromRate the rate value of the source currency (rate to base)
     * @param toRate   the rate value of the target currency (rate to base)
     * @return the raw cross rate from fromCurrency to toCurrency
     */
    public BigDecimal calculateRawCrossRate(BigDecimal fromRate, BigDecimal toRate) {
        Objects.requireNonNull(fromRate, "fromRate must not be null");
        Objects.requireNonNull(toRate, "toRate must not be null");

        if (fromRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fromRate must be positive: " + fromRate);
        }
        if (toRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("toRate must be positive: " + toRate);
        }

        return toRate.divide(fromRate, DIVISION_CONTEXT)
                .setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private void validateInputs(
            BigDecimal fromRate,
            BigDecimal toRate,
            String baseCurrencyCode,
            String fromCurrencyCode,
            String toCurrencyCode
    ) {
        Objects.requireNonNull(fromRate, "fromRate must not be null");
        Objects.requireNonNull(toRate, "toRate must not be null");
        Objects.requireNonNull(baseCurrencyCode, "baseCurrencyCode must not be null");
        Objects.requireNonNull(fromCurrencyCode, "fromCurrencyCode must not be null");
        Objects.requireNonNull(toCurrencyCode, "toCurrencyCode must not be null");

        if (fromRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fromRate must be positive: " + fromRate);
        }
        if (toRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("toRate must be positive: " + toRate);
        }
    }
}
