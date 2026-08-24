package com.example.marcuraexchangeratebackend.exchange.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for exchange rate calculation.
 *
 * @param from            the source currency code
 * @param to              the target currency code
 * @param exchange        the spread-adjusted exchange rate
 * @param date            the rate date used for the calculation
 * @param fromQueryCount  total query count for the from currency
 * @param toQueryCount    total query count for the to currency
 */
public record ExchangeResponse(
        String from,
        String to,
        BigDecimal exchange,
        LocalDate date,
        Long fromQueryCount,
        Long toQueryCount
) {
}
