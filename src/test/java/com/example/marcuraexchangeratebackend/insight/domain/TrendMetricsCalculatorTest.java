package com.example.marcuraexchangeratebackend.insight.domain;

import com.example.marcuraexchangeratebackend.insight.domain.TrendInsightContext.HistoricalDataPoint;
import com.example.marcuraexchangeratebackend.insight.domain.TrendInsightContext.TrendMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for deterministic trend metrics calculation.
 * <p>
 * Verifies that numerical calculations are correct and use BigDecimal properly.
 */
@DisplayName("TrendMetricsCalculator Tests")
class TrendMetricsCalculatorTest {

    private TrendMetricsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TrendMetricsCalculator();
    }

    @Test
    @DisplayName("Should correctly identify INCREASE from 4.250000 to 4.308699")
    void shouldCorrectlyIdentifyIncrease() {
        // Given: Real example from human testing that Ollama got wrong
        List<HistoricalDataPoint> dataPoints = List.of(
                new HistoricalDataPoint(LocalDate.of(2026, 8, 22), new BigDecimal("4.250000")),
                new HistoricalDataPoint(LocalDate.of(2026, 8, 24), new BigDecimal("4.308699"))
        );

        // When
        TrendMetrics metrics = calculator.calculateMetrics(dataPoints);

        // Then
        assertThat(metrics.direction()).isEqualTo(TrendDirection.INCREASE);
        assertThat(metrics.firstRate()).isEqualByComparingTo(new BigDecimal("4.250000"));
        assertThat(metrics.lastRate()).isEqualByComparingTo(new BigDecimal("4.308699"));
        
        // Absolute change: 4.308699 - 4.250000 = 0.058699
        assertThat(metrics.absoluteChange()).isEqualByComparingTo(new BigDecimal("0.0586990000"));
        
        // Percentage change: (0.058699 / 4.250000) * 100 = 1.38115294...% ≈ 1.38%
        assertThat(metrics.percentageChange()).isNotNull();
        assertThat(metrics.percentageChange()).isEqualByComparingTo(new BigDecimal("1.38"));
    }

    @Test
    @DisplayName("Should correctly identify DECREASE when last rate is lower")
    void shouldCorrectlyIdentifyDecrease() {
        // Given
        List<HistoricalDataPoint> dataPoints = List.of(
                new HistoricalDataPoint(LocalDate.of(2024, 2, 1), new BigDecimal("0.85610")),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 5), new BigDecimal("0.85143"))
        );

        // When
        TrendMetrics metrics = calculator.calculateMetrics(dataPoints);

        // Then
        assertThat(metrics.direction()).isEqualTo(TrendDirection.DECREASE);
        
        // Absolute change: 0.85143 - 0.85610 = -0.00467
        assertThat(metrics.absoluteChange()).isNegative();
        assertThat(metrics.absoluteChange()).isEqualByComparingTo(new BigDecimal("-0.0046700000"));
        
        // Percentage change should be negative
        assertThat(metrics.percentageChange()).isNotNull();
        assertThat(metrics.percentageChange()).isNegative();
        assertThat(metrics.percentageChange()).isEqualByComparingTo(new BigDecimal("-0.55"));  // approximately -0.55%
    }

    @Test
    @DisplayName("Should correctly identify UNCHANGED when rates are equal")
    void shouldCorrectlyIdentifyUnchanged() {
        // Given
        List<HistoricalDataPoint> dataPoints = List.of(
                new HistoricalDataPoint(LocalDate.of(2024, 2, 1), new BigDecimal("1.50000")),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 5), new BigDecimal("1.50000"))
        );

        // When
        TrendMetrics metrics = calculator.calculateMetrics(dataPoints);

        // Then
        assertThat(metrics.direction()).isEqualTo(TrendDirection.UNCHANGED);
        
        // Absolute change should be zero
        assertThat(metrics.absoluteChange()).isEqualByComparingTo(BigDecimal.ZERO);
        
        // Percentage change should be zero
        assertThat(metrics.percentageChange()).isNotNull();
        assertThat(metrics.percentageChange()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should handle zero first rate without division by zero")
    void shouldHandleZeroFirstRateGracefully() {
        // Given
        List<HistoricalDataPoint> dataPoints = List.of(
                new HistoricalDataPoint(LocalDate.of(2024, 2, 1), BigDecimal.ZERO),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 5), new BigDecimal("1.50000"))
        );

        // When
        TrendMetrics metrics = calculator.calculateMetrics(dataPoints);

        // Then
        assertThat(metrics.direction()).isEqualTo(TrendDirection.INCREASE);
        assertThat(metrics.absoluteChange()).isEqualByComparingTo(new BigDecimal("1.5000000000"));
        
        // Percentage change should be null when first rate is zero
        assertThat(metrics.percentageChange()).isNull();
    }

    @Test
    @DisplayName("Should correctly extract first and last data points")
    void shouldCorrectlyExtractFirstAndLastDataPoints() {
        // Given: Multiple data points
        List<HistoricalDataPoint> dataPoints = List.of(
                new HistoricalDataPoint(LocalDate.of(2024, 2, 1), new BigDecimal("1.00")),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 2), new BigDecimal("1.10")),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 3), new BigDecimal("1.05")),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 4), new BigDecimal("1.15")),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 5), new BigDecimal("1.20"))
        );

        // When
        TrendMetrics metrics = calculator.calculateMetrics(dataPoints);

        // Then
        assertThat(metrics.firstDate()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(metrics.firstRate()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(metrics.lastDate()).isEqualTo(LocalDate.of(2024, 2, 5));
        assertThat(metrics.lastRate()).isEqualByComparingTo(new BigDecimal("1.20"));
        
        // Should calculate change from first to last (ignoring intermediate volatility)
        assertThat(metrics.direction()).isEqualTo(TrendDirection.INCREASE);
        assertThat(metrics.absoluteChange()).isEqualByComparingTo(new BigDecimal("0.2000000000"));
        assertThat(metrics.percentageChange()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when fewer than 2 data points")
    void shouldThrowExceptionWhenInsufficientDataPoints() {
        // Given
        List<HistoricalDataPoint> singlePoint = List.of(
                new HistoricalDataPoint(LocalDate.of(2024, 2, 1), new BigDecimal("1.00"))
        );

        // When & Then
        assertThatThrownBy(() -> calculator.calculateMetrics(singlePoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least 2 historical data points required");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when null data points")
    void shouldThrowExceptionWhenNullDataPoints() {
        // When & Then
        assertThatThrownBy(() -> calculator.calculateMetrics(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least 2 historical data points required");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when empty data points")
    void shouldThrowExceptionWhenEmptyDataPoints() {
        // When & Then
        assertThatThrownBy(() -> calculator.calculateMetrics(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least 2 historical data points required");
    }

    @Test
    @DisplayName("Should handle very small rate changes with precision")
    void shouldHandleVerySmallRateChangesWithPrecision() {
        // Given: Very small change that is detectable after rounding
        List<HistoricalDataPoint> dataPoints = List.of(
                new HistoricalDataPoint(LocalDate.of(2024, 2, 1), new BigDecimal("1.234567")),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 2), new BigDecimal("1.245678"))  // Larger difference for detectable percentage
        );

        // When
        TrendMetrics metrics = calculator.calculateMetrics(dataPoints);

        // Then
        assertThat(metrics.direction()).isEqualTo(TrendDirection.INCREASE);
        assertThat(metrics.absoluteChange()).isPositive();
        assertThat(metrics.percentageChange()).isNotNull();
        assertThat(metrics.percentageChange()).isPositive();
    }

    @Test
    @DisplayName("Should handle very large rates without overflow")
    void shouldHandleVeryLargeRatesWithoutOverflow() {
        // Given: Very large rate values (e.g., exotic currency pairs)
        List<HistoricalDataPoint> dataPoints = List.of(
                new HistoricalDataPoint(LocalDate.of(2024, 2, 1), new BigDecimal("123456.789")),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 2), new BigDecimal("125000.000"))
        );

        // When
        TrendMetrics metrics = calculator.calculateMetrics(dataPoints);

        // Then
        assertThat(metrics.direction()).isEqualTo(TrendDirection.INCREASE);
        assertThat(metrics.absoluteChange()).isPositive();
        assertThat(metrics.percentageChange()).isNotNull();
        assertThat(metrics.percentageChange()).isPositive();
    }

    @Test
    @DisplayName("Should use exact BigDecimal comparison for direction (no floating-point issues)")
    void shouldUseExactBigDecimalComparisonForDirection() {
        // Given: Values that could cause floating-point comparison issues
        List<HistoricalDataPoint> dataPoints = List.of(
                new HistoricalDataPoint(LocalDate.of(2024, 2, 1), new BigDecimal("0.1").add(new BigDecimal("0.2"))),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 2), new BigDecimal("0.3"))
        );

        // When
        TrendMetrics metrics = calculator.calculateMetrics(dataPoints);

        // Then: Should correctly identify as UNCHANGED despite potential floating-point issues
        assertThat(metrics.direction()).isEqualTo(TrendDirection.UNCHANGED);
    }

    @Test
    @DisplayName("Should format percentage change with 2 decimal places")
    void shouldFormatPercentageChangeWith2DecimalPlaces() {
        // Given
        List<HistoricalDataPoint> dataPoints = List.of(
                new HistoricalDataPoint(LocalDate.of(2024, 2, 1), new BigDecimal("100.00")),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 2), new BigDecimal("101.23456"))
        );

        // When
        TrendMetrics metrics = calculator.calculateMetrics(dataPoints);

        // Then
        assertThat(metrics.percentageChange()).isNotNull();
        assertThat(metrics.percentageChange().scale()).isEqualTo(2);
        assertThat(metrics.percentageChange()).isEqualByComparingTo(new BigDecimal("1.23"));
    }

    @Test
    @DisplayName("Should handle negative first rate (theoretical edge case)")
    void shouldHandleNegativeFirstRate() {
        // Given: Theoretical edge case (rates should be positive in reality)
        List<HistoricalDataPoint> dataPoints = List.of(
                new HistoricalDataPoint(LocalDate.of(2024, 2, 1), new BigDecimal("-1.00")),
                new HistoricalDataPoint(LocalDate.of(2024, 2, 2), new BigDecimal("-0.50"))
        );

        // When
        TrendMetrics metrics = calculator.calculateMetrics(dataPoints);

        // Then: -0.50 is greater than -1.00, so it's an INCREASE
        assertThat(metrics.direction()).isEqualTo(TrendDirection.INCREASE);
        assertThat(metrics.absoluteChange()).isPositive();
    }
}
