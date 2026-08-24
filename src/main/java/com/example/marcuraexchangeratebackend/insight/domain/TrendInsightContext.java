package com.example.marcuraexchangeratebackend.insight.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable context object containing all information needed for AI trend insight generation.
 * <p>
 * Contains:
 * - Actual historical rate data (raw cross-rates)
 * - Application-calculated trend metrics (direction, percentage change, etc.)
 * <p>
 * The LLM must NOT recalculate these metrics - they are authoritative.
 * <p>
 * Does NOT contain JPA entities - uses simple DTOs.
 */
public record TrendInsightContext(
        String fromCurrency,
        String toCurrency,
        LocalDate fromDate,
        LocalDate toDate,
        List<HistoricalDataPoint> historicalRates,
        TrendMetrics metrics
) {
    /**
     * Single historical data point for a specific date.
     * <p>
     * Contains the raw cross-rate value (toRate / fromRate) without spread adjustment.
     * This is the value shown in the historical chart.
     *
     * @param date    the rate date
     * @param rawRate the raw cross-rate value (no spread applied)
     */
    public record HistoricalDataPoint(
            LocalDate date,
            BigDecimal rawRate
    ) {
    }

    /**
     * Application-calculated trend metrics.
     * <p>
     * These values are deterministically calculated by the backend and are AUTHORITATIVE.
     * <p>
     * The LLM must NOT recalculate direction or percentage change.
     *
     * @param firstDate       date of first available data point
     * @param firstRate       rate value of first available data point
     * @param lastDate        date of last available data point
     * @param lastRate        rate value of last available data point
     * @param absoluteChange  absolute change from first to last rate (last - first)
     * @param percentageChange percentage change from first to last rate ((last - first) / first * 100)
     *                        null if first rate is zero or calculation is not possible
     * @param direction       deterministic direction of rate movement
     */
    public record TrendMetrics(
            LocalDate firstDate,
            BigDecimal firstRate,
            LocalDate lastDate,
            BigDecimal lastRate,
            BigDecimal absoluteChange,
            BigDecimal percentageChange,
            TrendDirection direction
    ) {
    }

    /**
     * Check if sufficient data exists for meaningful trend analysis.
     *
     * @return true if at least 2 data points exist
     */
    public boolean hasSufficientData() {
        return historicalRates != null && historicalRates.size() >= 2;
    }

    /**
     * Check if context contains any historical data.
     *
     * @return true if at least 1 data point exists
     */
    public boolean hasData() {
        return historicalRates != null && !historicalRates.isEmpty();
    }
}
