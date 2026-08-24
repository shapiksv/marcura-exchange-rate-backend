package com.example.marcuraexchangeratebackend.rate.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection for historical rate query results.
 * <p>
 * Contains all required data for a single historical snapshot: date, base, and both currency rates.
 * <p>
 * Used to eliminate N+1 query pattern by fetching all needed data in a single database query.
 *
 * @param rateDate      the exchange rate date
 * @param baseCurrency  the base currency for this snapshot
 * @param fromRate      rate value for the source currency
 * @param toRate        rate value for the target currency
 */
public record HistoricalRateProjection(
        LocalDate rateDate,
        String baseCurrency,
        BigDecimal fromRate,
        BigDecimal toRate
) {
}
