package com.example.marcuraexchangeratebackend.exchange.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Result of an exchange calculation.
 *
 * @param from            the source currency code
 * @param to              the target currency code
 * @param exchange        the spread-adjusted exchange rate
 * @param date            the rate date used for the calculation
 * @param fromQueryCount  total query count for the from currency
 * @param toQueryCount    total query count for the to currency
 */
public record ExchangeResult(
        String from,
        String to,
        BigDecimal exchange,
        LocalDate date,
        Long fromQueryCount,
        Long toQueryCount
) {
}
