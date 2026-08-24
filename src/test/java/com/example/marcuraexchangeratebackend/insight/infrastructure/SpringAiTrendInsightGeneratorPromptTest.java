package com.example.marcuraexchangeratebackend.insight.infrastructure;

import com.example.marcuraexchangeratebackend.insight.domain.TrendDirection;
import com.example.marcuraexchangeratebackend.insight.domain.TrendInsightContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Focused tests for prompt construction in SpringAiTrendInsightGenerator.
 * <p>
 * These tests verify that the actual prompts sent to the AI layer contain
 * the correct constraints and historical data, plus application-calculated metrics.
 * <p>
 * Does NOT require a running Ollama instance.
 */
@DisplayName("SpringAiTrendInsightGenerator - Prompt Construction Tests")
class SpringAiTrendInsightGeneratorPromptTest {

    private SpringAiTrendInsightGenerator generator;

    @BeforeEach
    void setUp() {
        // We only need the generator instance to test prompt building methods
        // ChatModel is mocked but never called in these tests
        ChatModel mockChatModel = mock(ChatModel.class);
        generator = new SpringAiTrendInsightGenerator(mockChatModel);
    }

    // Helper method to create context with metrics
    private TrendInsightContext createContextWithMetrics(
            String from,
            String to,
            LocalDate fromDate,
            LocalDate toDate,
            List<TrendInsightContext.HistoricalDataPoint> dataPoints
    ) {
        if (dataPoints.isEmpty()) {
            return new TrendInsightContext(from, to, fromDate, toDate, dataPoints, null);
        }
        
        TrendInsightContext.HistoricalDataPoint firstPoint = dataPoints.get(0);
        TrendInsightContext.HistoricalDataPoint lastPoint = dataPoints.get(dataPoints.size() - 1);
        
        BigDecimal absoluteChange = lastPoint.rawRate().subtract(firstPoint.rawRate());
        BigDecimal percentageChange = absoluteChange
                .divide(firstPoint.rawRate(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        
        TrendDirection direction;
        if (lastPoint.rawRate().compareTo(firstPoint.rawRate()) > 0) {
            direction = TrendDirection.INCREASE;
        } else if (lastPoint.rawRate().compareTo(firstPoint.rawRate()) < 0) {
            direction = TrendDirection.DECREASE;
        } else {
            direction = TrendDirection.UNCHANGED;
        }
        
        TrendInsightContext.TrendMetrics metrics = new TrendInsightContext.TrendMetrics(
                firstPoint.date(),
                firstPoint.rawRate(),
                lastPoint.date(),
                lastPoint.rawRate(),
                absoluteChange,
                percentageChange,
                direction
        );
        
        return new TrendInsightContext(from, to, fromDate, toDate, dataPoints, metrics);
    }

    @Test
    @DisplayName("System prompt should prohibit recalculating direction")
    void systemPromptShouldProhibitRecalculatingDirection() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("Do NOT recalculate direction");
    }

    @Test
    @DisplayName("System prompt should prohibit recalculating percentage change")
    void systemPromptShouldProhibitRecalculatingPercentageChange() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("Do NOT recalculate percentage change");
    }

    @Test
    @DisplayName("System prompt should state application metrics are authoritative")
    void systemPromptShouldStateApplicationMetricsAreAuthoritative() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("application-calculated")
                .containsIgnoringCase("AUTHORITATIVE");
    }

    @Test
    @DisplayName("System prompt should prohibit contradicting supplied direction")
    void systemPromptShouldProhibitContradictingSuppliedDirection() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("Do NOT contradict the supplied direction");
    }

    @Test
    @DisplayName("System prompt should prohibit contradicting numerical metrics")
    void systemPromptShouldProhibitContradictingNumericalMetrics() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("Do NOT contradict the supplied numerical metrics");
    }

    @Test
    @DisplayName("System prompt should prohibit financial advice")
    void systemPromptShouldProhibitFinancialAdvice() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("Do not provide financial advice");
    }

    @Test
    @DisplayName("System prompt should prohibit invented news/causes")
    void systemPromptShouldProhibitInventedCauses() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("Do not invent news")
                .containsIgnoringCase("geopolitical events")
                .containsIgnoringCase("market explanations");
    }

    @Test
    @DisplayName("System prompt should prohibit unsupported predictions")
    void systemPromptShouldProhibitUnsupportedPredictions() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("Do not make unsupported predictions");
    }

    @Test
    @DisplayName("System prompt should constrain output to maximum 2 short sentences")
    void systemPromptShouldConstrainOutputLength() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("maximum 2 short sentences");
    }

    @Test
    @DisplayName("System prompt should require use of only provided numbers")
    void systemPromptShouldRequireUseOfOnlyProvidedNumbers() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("Use ONLY the application-calculated");
    }

    @Test
    @DisplayName("System prompt should prohibit describing rates as market concepts")
    void systemPromptShouldProhibitMarketConcepts() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("Do NOT describe the first or last available point")
                .containsIgnoringCase("daily close")
                .containsIgnoringCase("opening rate");
    }

    @Test
    @DisplayName("System prompt should instruct to describe absolute change correctly")
    void systemPromptShouldInstructAbsoluteChangeDescription() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("absolute change")
                .containsIgnoringCase("difference between the first and last available rates");
    }

    @Test
    @DisplayName("System prompt should prohibit inferring consecutive days")
    void systemPromptShouldProhibitInferringConsecutiveDays() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("Do NOT infer that historical points are consecutive days");
    }

    @Test
    @DisplayName("System prompt should prohibit inventing information about missing dates")
    void systemPromptShouldProhibitInventingMissingDates() {
        String systemPrompt = SpringAiTrendInsightGenerator.SYSTEM_PROMPT;

        assertThat(systemPrompt)
                .containsIgnoringCase("Do NOT invent information about missing dates");
    }

    @Test
    @DisplayName("User prompt should include currency pair")
    void userPromptShouldIncludeCurrencyPair() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2024, 2, 1), new BigDecimal("0.85")),
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2024, 2, 5), new BigDecimal("0.86"))
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        assertThat(userPrompt)
                .contains("Currency pair: EUR/GBP");
    }

    @Test
    @DisplayName("User prompt should include date range")
    void userPromptShouldIncludeDateRange() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2024, 2, 1), new BigDecimal("0.85")),
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2024, 2, 28), new BigDecimal("0.86"))
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 28),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        assertThat(userPrompt)
                .contains("Requested period: 2024-02-01 to 2024-02-28");
    }

    @Test
    @DisplayName("User prompt should include first available rate with date")
    void userPromptShouldIncludeFirstAvailableRate() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 22), new BigDecimal("4.250000")),
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 24), new BigDecimal("4.308699"))
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "PLN",
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 24),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        assertThat(userPrompt)
                .contains("First available rate:")
                .contains("2026-08-22: 4.25");
    }

    @Test
    @DisplayName("User prompt should include last available rate with date")
    void userPromptShouldIncludeLastAvailableRate() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 22), new BigDecimal("4.250000")),
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 24), new BigDecimal("4.308699"))
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "PLN",
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 24),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        assertThat(userPrompt)
                .contains("Last available rate:")
                .contains("2026-08-24: 4.308699");
    }

    @Test
    @DisplayName("User prompt should include application-calculated direction")
    void userPromptShouldIncludeApplicationCalculatedDirection() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 22), new BigDecimal("4.250000")),
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 24), new BigDecimal("4.308699"))
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "PLN",
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 24),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        assertThat(userPrompt)
                .contains("Application-calculated metrics")
                .contains("Direction: INCREASE");
    }

    @Test
    @DisplayName("User prompt should include application-calculated absolute change")
    void userPromptShouldIncludeApplicationCalculatedAbsoluteChange() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 22), new BigDecimal("4.250000")),
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 24), new BigDecimal("4.308699"))
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "PLN",
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 24),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        assertThat(userPrompt)
                .contains("Absolute change:");
    }

    @Test
    @DisplayName("User prompt should include application-calculated percentage change")
    void userPromptShouldIncludeApplicationCalculatedPercentageChange() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 22), new BigDecimal("4.250000")),
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 24), new BigDecimal("4.308699"))
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "PLN",
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 24),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        assertThat(userPrompt)
                .contains("Percentage change:")
                .contains("%");
    }

    @Test
    @DisplayName("User prompt should mark metrics as AUTHORITATIVE")
    void userPromptShouldMarkMetricsAsAuthoritative() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 22), new BigDecimal("4.250000")),
                new TrendInsightContext.HistoricalDataPoint(LocalDate.of(2026, 8, 24), new BigDecimal("4.308699"))
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "PLN",
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 24),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        assertThat(userPrompt)
                .contains("AUTHORITATIVE")
                .contains("do NOT recalculate");
    }

    @Test
    @DisplayName("User prompt should include every supplied historical date")
    void userPromptShouldIncludeEverySuppliedHistoricalDate() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("0.85421")
                ),
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 2),
                        new BigDecimal("0.85610")
                ),
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 5),
                        new BigDecimal("0.85143")
                )
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        assertThat(userPrompt)
                .contains("2024-02-01")
                .contains("2024-02-02")
                .contains("2024-02-05");
    }

    @Test
    @DisplayName("User prompt should include every supplied raw historical rate")
    void userPromptShouldIncludeEverySuppliedRawHistoricalRate() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("0.85421")
                ),
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 2),
                        new BigDecimal("0.85610")
                ),
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 5),
                        new BigDecimal("0.85143")
                )
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        // stripTrailingZeros() converts "0.85610" to "0.8561"
        assertThat(userPrompt)
                .contains("0.85421")
                .contains("0.8561")  // trailing zero stripped
                .contains("0.85143");
    }

    @Test
    @DisplayName("User prompt should NOT fabricate missing dates")
    void userPromptShouldNotFabricateMissingDates() {
        // Historical data for Feb 1, 2, and 5 (missing Feb 3 and 4)
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("0.85421")
                ),
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 2),
                        new BigDecimal("0.85610")
                ),
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 5),
                        new BigDecimal("0.85143")
                )
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        // Verify missing dates are NOT in the prompt
        assertThat(userPrompt)
                .doesNotContain("2024-02-03")
                .doesNotContain("2024-02-04");
    }

    @Test
    @DisplayName("User prompt should include section header for historical rates")
    void userPromptShouldIncludeSectionHeaderForHistoricalRates() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("0.85421")
                ),
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 2),
                        new BigDecimal("0.85500")
                )
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 2),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        assertThat(userPrompt)
                .contains("Historical raw cross-rates:");
    }

    @Test
    @DisplayName("User prompt should preserve exact BigDecimal precision")
    void userPromptShouldPreserveExactBigDecimalPrecision() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("1.23456789012345")
                ),
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 2),
                        new BigDecimal("1.23456789012346")
                )
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "USD",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 2),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        assertThat(userPrompt)
                .contains("1.23456789012345");
    }

    @Test
    @DisplayName("formatRate should avoid scientific notation for large values")
    void formatRateShouldAvoidScientificNotationForLargeValues() {
        BigDecimal largeRate = new BigDecimal("1234567.89");

        String formatted = generator.formatRate(largeRate);

        assertThat(formatted)
                .isEqualTo("1234567.89")
                .doesNotContain("E")
                .doesNotContain("e");
    }

    @Test
    @DisplayName("formatRate should strip trailing zeros")
    void formatRateShouldStripTrailingZeros() {
        BigDecimal rateWithTrailingZeros = new BigDecimal("0.85000");

        String formatted = generator.formatRate(rateWithTrailingZeros);

        assertThat(formatted)
                .isEqualTo("0.85");
    }

    @Test
    @DisplayName("formatRate should handle very small rates without scientific notation")
    void formatRateShouldHandleVerySmallRatesWithoutScientificNotation() {
        BigDecimal smallRate = new BigDecimal("0.00012345");

        String formatted = generator.formatRate(smallRate);

        assertThat(formatted)
                .isEqualTo("0.00012345")
                .doesNotContain("E")
                .doesNotContain("e");
    }

    @Test
    @DisplayName("User prompt should maintain deterministic structure")
    void userPromptShouldMaintainDeterministicStructure() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("0.85421")
                ),
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 3),
                        new BigDecimal("0.85143")
                )
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 3),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        // Verify deterministic structure
        assertThat(userPrompt)
                .startsWith("Currency pair: EUR/GBP\n")
                .contains("Requested period: 2024-02-01 to 2024-02-03\n")
                .contains("Historical raw cross-rates:\n")
                .contains("2024-02-01: 0.85421\n")
                .contains("2024-02-03: 0.85143\n")
                .endsWith("Do NOT recalculate.");
    }

    @Test
    @DisplayName("User prompt should format dates in ISO format")
    void userPromptShouldFormatDatesInIsoFormat() {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = List.of(
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("0.85421")
                ),
                new TrendInsightContext.HistoricalDataPoint(
                        LocalDate.of(2024, 2, 2),
                        new BigDecimal("0.85500")
                )
        );
        
        TrendInsightContext context = createContextWithMetrics(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 2),
                dataPoints
        );

        String userPrompt = generator.buildUserPrompt(context);

        // ISO format is YYYY-MM-DD
        assertThat(userPrompt)
                .contains("2024-02-01")
                .doesNotContain("Feb")
                .doesNotContain("February")
                .doesNotContain("02/01/2024")
                .doesNotContain("01/02/2024");
    }
}
