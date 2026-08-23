package com.example.marcuraexchangeratebackend.exchange.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExchangeRateCalculator.
 *
 * Tests include the EUR/PLN worked example from the assessment.
 */
class ExchangeRateCalculatorTest {

    private ExchangeRateCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ExchangeRateCalculator();
    }

    @Test
    void testWorkedExampleEurToPlnFromAssessment() {
        // From assessment:
        // EUR -> PLN with USD base
        // EUR to USD rate: 1.08360
        // PLN to USD rate: 4.56734
        // Both have default spread: 2.75%
        // Expected formula: (4.56734 / 1.08360) * ((100 - 2.75) / 100)

        BigDecimal eurToUsdRate = new BigDecimal("1.08360");
        BigDecimal plnToUsdRate = new BigDecimal("4.56734");

        BigDecimal adjustedRate = calculator.calculateAdjustedRate(
                eurToUsdRate,      // from rate (EUR to USD)
                plnToUsdRate,      // to rate (PLN to USD)
                "USD",             // base currency
                "EUR",             // from currency
                "PLN"              // to currency
        );

        // Cross rate: 4.56734 / 1.08360 ≈ 4.214968623
        // Spread multiplier: (100 - 2.75) / 100 = 0.9725
        // Adjusted: 4.214968623 * 0.9725 ≈ 4.099056986

        // Allow small tolerance due to BigDecimal precision
        BigDecimal expected = new BigDecimal("4.0990569860");
        assertEquals(0, adjustedRate.compareTo(expected),
                "Adjusted rate should be " + expected + " but was " + adjustedRate);
    }

    @Test
    void testSameCurrencyExchange() {
        // EUR to EUR should result in 1.0 (before spread)
        BigDecimal eurRate = new BigDecimal("1.08360");

        BigDecimal adjustedRate = calculator.calculateAdjustedRate(
                eurRate,
                eurRate,
                "USD",
                "EUR",
                "EUR"
        );

        // Same currency: rate = 1.08360 / 1.08360 = 1.0
        // Spread: EUR is not base, so 2.75%
        // Adjusted: 1.0 * ((100 - 2.75) / 100) = 0.9725
        BigDecimal expected = new BigDecimal("0.9725000000");
        assertEquals(expected, adjustedRate);
    }

    @Test
    void testBaseCurrencyHasZeroSpread() {
        // USD (base) to EUR
        BigDecimal usdRate = new BigDecimal("1.0");  // Base currency rate is 1.0
        BigDecimal eurRate = new BigDecimal("1.08360");

        BigDecimal adjustedRate = calculator.calculateAdjustedRate(
                usdRate,
                eurRate,
                "USD",
                "USD",  // from is base
                "EUR"
        );

        // Cross rate: 1.08360 / 1.0 = 1.08360
        // USD is base (0%), EUR is default (2.75%), max = 2.75%
        // Adjusted: 1.08360 * 0.9725 = 1.05385...
        BigDecimal expected = new BigDecimal("1.0538010000");
        assertEquals(expected, adjustedRate);
    }

    @Test
    void testHigherSpreadIsUsed() {
        // EUR (2.75%) to JPY (3.25%)
        BigDecimal eurRate = new BigDecimal("1.08360");
        BigDecimal jpyRate = new BigDecimal("140.50");

        BigDecimal adjustedRate = calculator.calculateAdjustedRate(
                eurRate,
                jpyRate,
                "USD",
                "EUR",
                "JPY"
        );

        // Cross rate: 140.50 / 1.08360 = 129.640...
        // Higher spread: max(2.75, 3.25) = 3.25%
        // Adjusted: 129.640... * ((100 - 3.25) / 100) = 129.640... * 0.9675
        BigDecimal crossRate = jpyRate.divide(eurRate, java.math.MathContext.DECIMAL128);
        BigDecimal expected = crossRate.multiply(new BigDecimal("0.9675")).setScale(10, java.math.RoundingMode.HALF_UP);

        assertEquals(expected, adjustedRate);
    }

    @Test
    void testRawCrossRateCalculation() {
        BigDecimal eurRate = new BigDecimal("1.08360");
        BigDecimal plnRate = new BigDecimal("4.56734");

        BigDecimal crossRate = calculator.calculateRawCrossRate(eurRate, plnRate);

        // 4.56734 / 1.08360 = 4.214968623...
        BigDecimal expected = new BigDecimal("4.2149686231");
        assertEquals(expected, crossRate);
    }

    @Test
    void testNullInputsThrowException() {
        BigDecimal rate = new BigDecimal("1.0");

        assertThrows(NullPointerException.class, () ->
                calculator.calculateAdjustedRate(null, rate, "USD", "EUR", "PLN"));

        assertThrows(NullPointerException.class, () ->
                calculator.calculateAdjustedRate(rate, null, "USD", "EUR", "PLN"));

        assertThrows(NullPointerException.class, () ->
                calculator.calculateAdjustedRate(rate, rate, null, "EUR", "PLN"));

        assertThrows(NullPointerException.class, () ->
                calculator.calculateAdjustedRate(rate, rate, "USD", null, "PLN"));

        assertThrows(NullPointerException.class, () ->
                calculator.calculateAdjustedRate(rate, rate, "USD", "EUR", null));
    }

    @Test
    void testNegativeRatesThrowException() {
        BigDecimal positiveRate = new BigDecimal("1.0");
        BigDecimal negativeRate = new BigDecimal("-1.0");

        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateAdjustedRate(negativeRate, positiveRate, "USD", "EUR", "PLN"));

        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateAdjustedRate(positiveRate, negativeRate, "USD", "EUR", "PLN"));
    }

    @Test
    void testZeroRatesThrowException() {
        BigDecimal positiveRate = new BigDecimal("1.0");
        BigDecimal zeroRate = BigDecimal.ZERO;

        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateAdjustedRate(zeroRate, positiveRate, "USD", "EUR", "PLN"));

        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculateAdjustedRate(positiveRate, zeroRate, "USD", "EUR", "PLN"));
    }

    @Test
    void testPrecisionIsMaintained() {
        // Test that we don't lose precision with small numbers
        BigDecimal smallRate1 = new BigDecimal("0.0001234567");
        BigDecimal smallRate2 = new BigDecimal("0.0005678901");

        BigDecimal result = calculator.calculateRawCrossRate(smallRate1, smallRate2);

        assertNotNull(result);
        assertTrue(result.scale() == 10, "Result should have 10 decimal places");
        assertTrue(result.compareTo(BigDecimal.ZERO) > 0, "Result should be positive");
    }
}
