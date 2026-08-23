package com.example.marcuraexchangeratebackend.exchange.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CurrencySpread enum.
 */
class CurrencySpreadTest {

    @Test
    void testBaseCurrencySpread() {
        // Base currency from Fixer.io should have 0% spread
        BigDecimal spread = CurrencySpread.getSpreadFor("EUR", true);
        assertEquals(BigDecimal.ZERO, spread);
    }

    @Test
    void testTier1Spread() {
        // JPY, HKD, KRW: 3.25%
        assertEquals(new BigDecimal("3.25"), CurrencySpread.getSpreadFor("JPY", false));
        assertEquals(new BigDecimal("3.25"), CurrencySpread.getSpreadFor("HKD", false));
        assertEquals(new BigDecimal("3.25"), CurrencySpread.getSpreadFor("KRW", false));
    }

    @Test
    void testTier2Spread() {
        // MYR, INR, MXN: 4.50%
        assertEquals(new BigDecimal("4.50"), CurrencySpread.getSpreadFor("MYR", false));
        assertEquals(new BigDecimal("4.50"), CurrencySpread.getSpreadFor("INR", false));
        assertEquals(new BigDecimal("4.50"), CurrencySpread.getSpreadFor("MXN", false));
    }

    @Test
    void testTier3Spread() {
        // RUB, CNY, ZAR: 6.00%
        assertEquals(new BigDecimal("6.00"), CurrencySpread.getSpreadFor("RUB", false));
        assertEquals(new BigDecimal("6.00"), CurrencySpread.getSpreadFor("CNY", false));
        assertEquals(new BigDecimal("6.00"), CurrencySpread.getSpreadFor("ZAR", false));
    }

    @Test
    void testDefaultSpread() {
        // All other currencies: 2.75%
        assertEquals(new BigDecimal("2.75"), CurrencySpread.getSpreadFor("USD", false));
        assertEquals(new BigDecimal("2.75"), CurrencySpread.getSpreadFor("GBP", false));
        assertEquals(new BigDecimal("2.75"), CurrencySpread.getSpreadFor("PLN", false));
        assertEquals(new BigDecimal("2.75"), CurrencySpread.getSpreadFor("EUR", false));
    }

    @Test
    void testHigherSpreadSelection() {
        // EUR (2.75%) vs PLN (2.75%) -> max = 2.75%
        BigDecimal spread1 = CurrencySpread.getHigherSpread("EUR", "PLN", "USD");
        assertEquals(new BigDecimal("2.75"), spread1);

        // EUR (2.75%) vs JPY (3.25%) -> max = 3.25%
        BigDecimal spread2 = CurrencySpread.getHigherSpread("EUR", "JPY", "USD");
        assertEquals(new BigDecimal("3.25"), spread2);

        // RUB (6.00%) vs INR (4.50%) -> max = 6.00%
        BigDecimal spread3 = CurrencySpread.getHigherSpread("RUB", "INR", "USD");
        assertEquals(new BigDecimal("6.00"), spread3);
    }

    @Test
    void testBaseCurrencyInSpreadCalculation() {
        // When one currency is the base, it should have 0% spread
        // USD (base) vs EUR (2.75%) -> max = 2.75%
        BigDecimal spread = CurrencySpread.getHigherSpread("USD", "EUR", "USD");
        assertEquals(new BigDecimal("2.75"), spread);

        // EUR (base) vs PLN (2.75%) -> max = 2.75%
        BigDecimal spread2 = CurrencySpread.getHigherSpread("EUR", "PLN", "EUR");
        assertEquals(new BigDecimal("2.75"), spread2);
    }

    @Test
    void testBothCurrenciesAreBase() {
        // When both are base (same currency), spread should be 0%
        BigDecimal spread = CurrencySpread.getHigherSpread("EUR", "EUR", "EUR");
        assertEquals(BigDecimal.ZERO, spread);
    }
}
