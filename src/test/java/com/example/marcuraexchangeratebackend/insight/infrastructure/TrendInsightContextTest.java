package com.example.marcuraexchangeratebackend.insight.infrastructure;

import com.example.marcuraexchangeratebackend.insight.domain.TrendInsightContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TrendInsightContext structure.
 * <p>
 * Tests data preservation and helper methods.
 */
class TrendInsightContextTest {

    @Test
    @DisplayName("TrendInsightContext should include historical data points")
    void contextShouldIncludeHistoricalDataPoints() {
        // Given
        List<TrendInsightContext.HistoricalDataPoint> points = List.of(
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("0.85421")
                ),
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 2),
                        new BigDecimal("0.85610")
                )
        );

        // When
        TrendInsightContext context = new TrendInsightContext(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5),
                points,
                null  // metrics not needed for this test
        );

        // Then
        assertThat(context.historicalRates()).hasSize(2);
        assertThat(context.historicalRates().get(0).rawRate())
                .isEqualByComparingTo(new BigDecimal("0.85421"));
        assertThat(context.historicalRates().get(1).rawRate())
                .isEqualByComparingTo(new BigDecimal("0.85610"));
    }

    @Test
    @DisplayName("TrendInsightContext should preserve currency pair")
    void contextShouldPreserveCurrencyPair() {
        // Given / When
        TrendInsightContext context = createMinimalContext("USD", "JPY");

        // Then
        assertThat(context.fromCurrency()).isEqualTo("USD");
        assertThat(context.toCurrency()).isEqualTo("JPY");
    }

    @Test
    @DisplayName("TrendInsightContext should preserve date range")
    void contextShouldPreserveDateRange() {
        // Given
        LocalDate fromDate = LocalDate.of(2024, 3, 1);
        LocalDate toDate = LocalDate.of(2024, 3, 31);

        // When
        TrendInsightContext context = new TrendInsightContext(
                "EUR",
                "USD",
                fromDate,
                toDate,
                List.of(new TrendInsightContext.HistoricalDataPoint(fromDate, BigDecimal.ONE)),
                null  // metrics not needed for this test
        );

        // Then
        assertThat(context.fromDate()).isEqualTo(fromDate);
        assertThat(context.toDate()).isEqualTo(toDate);
    }

    @Test
    @DisplayName("TrendInsightContext should report sufficient data when 2+ points exist")
    void contextShouldReportSufficientDataWhenTwoOrMorePoints() {
        // Given
        TrendInsightContext context = new TrendInsightContext(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5),
                List.of(
                        new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2024, 2, 1), BigDecimal.ONE),
                        new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2024, 2, 5), BigDecimal.ONE)
                ),
                null  // metrics not needed for this test
        );

        // Then
        assertThat(context.hasSufficientData()).isTrue();
    }

    @Test
    @DisplayName("TrendInsightContext should report insufficient data when only 1 point exists")
    void contextShouldReportInsufficientDataWhenOnlyOnePoint() {
        // Given
        TrendInsightContext context = new TrendInsightContext(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5),
                List.of(
                        new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2024, 2, 1), BigDecimal.ONE)
                ),
                null  // metrics not needed for this test
        );

        // Then
        assertThat(context.hasSufficientData()).isFalse();
        assertThat(context.hasData()).isTrue();
    }

    @Test
    @DisplayName("TrendInsightContext should report no data when empty")
    void contextShouldReportNoDataWhenEmpty() {
        // Given
        TrendInsightContext context = new TrendInsightContext(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5),
                List.of(),
                null  // metrics not needed for this test
        );

        // Then
        assertThat(context.hasData()).isFalse();
        assertThat(context.hasSufficientData()).isFalse();
    }

    @Test
    @DisplayName("HistoricalDataPoint should preserve date and rate")
    void dataPointShouldPreserveDateAndRate() {
        // Given
        LocalDate date = LocalDate.of(2024, 2, 15);
        BigDecimal rate = new BigDecimal("0.85143");

        // When
        TrendInsightContext.HistoricalDataPoint point =
                new TrendInsightContext.HistoricalDataPoint(date, rate);

        // Then
        assertThat(point.date()).isEqualTo(date);
        assertThat(point.rawRate()).isEqualByComparingTo(rate);
    }

    private TrendInsightContext createMinimalContext(String from, String to) {
        return new TrendInsightContext(
                from,
                to,
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5),
                List.of(
                        new TrendInsightContext.HistoricalDataPoint(
                                LocalDate.of(2024, 2, 1),
                                BigDecimal.ONE
                        )
                ),
                null  // metrics not needed for this test
        );
    }
}
