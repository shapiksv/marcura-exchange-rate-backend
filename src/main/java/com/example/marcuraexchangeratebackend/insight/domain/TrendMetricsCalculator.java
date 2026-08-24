package com.example.marcuraexchangeratebackend.insight.domain;

import com.example.marcuraexchangeratebackend.insight.domain.TrendInsightContext.TrendMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Deterministic calculator for trend metrics.
 * <p>
 * Performs all numerical calculations that must NOT be delegated to the LLM.
 * <p>
 * Uses BigDecimal arithmetic with explicit scale and rounding to ensure accuracy.
 */
@Component
public class TrendMetricsCalculator {

    private static final Logger log = LoggerFactory.getLogger(TrendMetricsCalculator.class);

    /**
     * Scale for percentage change display (2 decimal places: e.g., 1.38%).
     */
    private static final int PERCENTAGE_SCALE = 2;

    /**
     * Scale for absolute change (preserves precision from rates).
     */
    private static final int ABSOLUTE_CHANGE_SCALE = 10;

    /**
     * Rounding mode for calculations.
     */
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Calculate trend metrics from a list of historical data points.
     * <p>
     * The list must contain at least 2 points and must be ordered by date ascending.
     * <p>
     * Calculations:
     * - absoluteChange = lastRate - firstRate
     * - percentageChange = ((lastRate - firstRate) / firstRate) * 100
     * - direction = INCREASE if lastRate > firstRate, DECREASE if lastRate < firstRate, UNCHANGED if equal
     * <p>
     * If firstRate is zero, percentageChange will be null and direction will be based on absolute change.
     *
     * @param historicalDataPoints ordered list of historical data points (at least 2 required)
     * @return calculated trend metrics
     * @throws IllegalArgumentException if list has fewer than 2 points
     */
    public TrendMetrics calculateMetrics(List<TrendInsightContext.HistoricalDataPoint> historicalDataPoints) {
        if (historicalDataPoints == null || historicalDataPoints.size() < 2) {
            throw new IllegalArgumentException(
                    "At least 2 historical data points required for trend metrics calculation"
            );
        }

        // Assume list is ordered by date ascending
        TrendInsightContext.HistoricalDataPoint firstPoint = historicalDataPoints.get(0);
        TrendInsightContext.HistoricalDataPoint lastPoint = historicalDataPoints.get(historicalDataPoints.size() - 1);

        BigDecimal firstRate = firstPoint.rawRate();
        BigDecimal lastRate = lastPoint.rawRate();

        log.debug("Calculating metrics: firstRate={}, lastRate={}", firstRate, lastRate);

        // Calculate absolute change
        BigDecimal absoluteChange = lastRate.subtract(firstRate)
                .setScale(ABSOLUTE_CHANGE_SCALE, ROUNDING_MODE);

        // Calculate percentage change (handle zero first rate)
        BigDecimal percentageChange = null;
        if (firstRate.compareTo(BigDecimal.ZERO) != 0) {
            percentageChange = lastRate.subtract(firstRate)
                    .divide(firstRate, PERCENTAGE_SCALE + 2, ROUNDING_MODE)  // extra precision for intermediate calculation
                    .multiply(new BigDecimal("100"))
                    .setScale(PERCENTAGE_SCALE, ROUNDING_MODE);
        } else {
            log.warn("First rate is zero - percentage change cannot be calculated");
        }

        // Determine direction
        TrendDirection direction = determineDirection(firstRate, lastRate);

        log.info("Calculated metrics: absoluteChange={}, percentageChange={}, direction={}",
                absoluteChange, percentageChange, direction);

        return new TrendMetrics(
                firstPoint.date(),
                firstRate,
                lastPoint.date(),
                lastRate,
                absoluteChange,
                percentageChange,
                direction
        );
    }

    /**
     * Determine trend direction by comparing first and last rates.
     * <p>
     * Uses BigDecimal.compareTo() for exact comparison (no floating-point issues).
     *
     * @param firstRate the first rate value
     * @param lastRate  the last rate value
     * @return INCREASE, DECREASE, or UNCHANGED
     */
    private TrendDirection determineDirection(BigDecimal firstRate, BigDecimal lastRate) {
        int comparison = lastRate.compareTo(firstRate);

        if (comparison > 0) {
            return TrendDirection.INCREASE;
        } else if (comparison < 0) {
            return TrendDirection.DECREASE;
        } else {
            return TrendDirection.UNCHANGED;
        }
    }
}
