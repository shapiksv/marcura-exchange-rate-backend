package com.example.marcuraexchangeratebackend.insight.application;

import com.example.marcuraexchangeratebackend.common.error.RateNotFoundException;
import com.example.marcuraexchangeratebackend.exchange.api.HistoricalRateEntry;
import com.example.marcuraexchangeratebackend.exchange.application.ExchangeHistoryService;
import com.example.marcuraexchangeratebackend.insight.domain.AiProviderException;
import com.example.marcuraexchangeratebackend.insight.domain.TrendInsightContext;
import com.example.marcuraexchangeratebackend.insight.domain.TrendInsightGenerator;
import com.example.marcuraexchangeratebackend.insight.domain.TrendMetricsCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for TrendInsightService.
 * <p>
 * Verifies orchestration logic, data validation, metrics calculation delegation, and AI delegation.
 */
@ExtendWith(MockitoExtension.class)
class TrendInsightServiceTest {

    @Mock
    private ExchangeHistoryService exchangeHistoryService;

    @Mock
    private TrendInsightGenerator trendInsightGenerator;

    @Captor
    private ArgumentCaptor<TrendInsightContext> contextCaptor;

    private TrendInsightService service;
    private TrendMetricsCalculator metricsCalculator;

    @BeforeEach
    void setUp() {
        // Use real TrendMetricsCalculator for integration-style testing
        metricsCalculator = new TrendMetricsCalculator();
        service = new TrendInsightService(exchangeHistoryService, trendInsightGenerator, metricsCalculator);
    }

    @Test
    @DisplayName("Should delegate to generator with valid historical data")
    void shouldDelegateToGeneratorWithValidData() {
        // Given
        List<HistoricalRateEntry> mockHistory = createMockHistory();
        
        when(exchangeHistoryService.getHistoricalRates(
                eq("EUR"), eq("GBP"), any(), any()))
                .thenReturn(mockHistory);
        
        when(trendInsightGenerator.generateInsight(any()))
                .thenReturn("EUR/GBP declined by 2%.");

        // When
        String result = service.generateTrendInsight(
                "EUR", "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5)
        );

        // Then
        assertThat(result).isEqualTo("EUR/GBP declined by 2%.");
        verify(trendInsightGenerator).generateInsight(any(TrendInsightContext.class));
    }

    @Test
    @DisplayName("Should pass actual historical data to generator")
    void shouldPassActualHistoricalDataToGenerator() {
        // Given
        List<HistoricalRateEntry> mockHistory = List.of(
                new HistoricalRateEntry(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("0.85421"),
                        new BigDecimal("0.83")
                ),
                new HistoricalRateEntry(
                        LocalDate.of(2024, 2, 2),
                        new BigDecimal("0.85610"),
                        new BigDecimal("0.84")
                )
        );
        
        when(exchangeHistoryService.getHistoricalRates(any(), any(), any(), any()))
                .thenReturn(mockHistory);
        
        when(trendInsightGenerator.generateInsight(any()))
                .thenReturn("Test insight");

        // When
        service.generateTrendInsight(
                "EUR", "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5)
        );

        // Then
        verify(trendInsightGenerator).generateInsight(contextCaptor.capture());
        
        TrendInsightContext capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.historicalRates()).hasSize(2);
        assertThat(capturedContext.historicalRates().get(0).rawRate())
                .isEqualByComparingTo(new BigDecimal("0.85421"));
        assertThat(capturedContext.historicalRates().get(1).rawRate())
                .isEqualByComparingTo(new BigDecimal("0.85610"));
    }

    @Test
    @DisplayName("Should throw RateNotFoundException when no historical data exists")
    void shouldThrowRateNotFoundWhenNoData() {
        // Given
        when(exchangeHistoryService.getHistoricalRates(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // When & Then
        assertThatThrownBy(() ->
                service.generateTrendInsight(
                        "EUR", "XXX",
                        LocalDate.of(2024, 2, 1),
                        LocalDate.of(2024, 2, 5)
                )
        )
                .isInstanceOf(RateNotFoundException.class)
                .hasMessageContaining("No historical rates available");

        verifyNoInteractions(trendInsightGenerator);
    }

    @Test
    @DisplayName("Should return deterministic message when only 1 data point exists")
    void shouldReturnDeterministicMessageWhenInsufficientData() {
        // Given
        List<HistoricalRateEntry> singlePoint = List.of(
                new HistoricalRateEntry(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("0.85"),
                        new BigDecimal("0.83")
                )
        );
        
        when(exchangeHistoryService.getHistoricalRates(any(), any(), any(), any()))
                .thenReturn(singlePoint);

        // When
        String result = service.generateTrendInsight(
                "EUR", "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5)
        );

        // Then
        assertThat(result).contains("Insufficient historical data");
        verifyNoInteractions(trendInsightGenerator);
    }

    @Test
    @DisplayName("Should propagate AiProviderException when generator fails")
    void shouldPropagateAiProviderException() {
        // Given
        when(exchangeHistoryService.getHistoricalRates(any(), any(), any(), any()))
                .thenReturn(createMockHistory());
        
        when(trendInsightGenerator.generateInsight(any()))
                .thenThrow(new AiProviderException("Ollama unavailable"));

        // When & Then
        assertThatThrownBy(() ->
                service.generateTrendInsight(
                        "EUR", "GBP",
                        LocalDate.of(2024, 2, 1),
                        LocalDate.of(2024, 2, 5)
                )
        )
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Ollama unavailable");
    }

    @Test
    @DisplayName("Should build context with correct currency pair and date range")
    void shouldBuildContextWithCorrectParameters() {
        // Given
        when(exchangeHistoryService.getHistoricalRates(any(), any(), any(), any()))
                .thenReturn(createMockHistory());
        
        when(trendInsightGenerator.generateInsight(any()))
                .thenReturn("Test insight");

        LocalDate fromDate = LocalDate.of(2024, 3, 1);
        LocalDate toDate = LocalDate.of(2024, 3, 31);

        // When
        service.generateTrendInsight("USD", "JPY", fromDate, toDate);

        // Then
        verify(trendInsightGenerator).generateInsight(contextCaptor.capture());
        
        TrendInsightContext context = contextCaptor.getValue();
        assertThat(context.fromCurrency()).isEqualTo("USD");
        assertThat(context.toCurrency()).isEqualTo("JPY");
        assertThat(context.fromDate()).isEqualTo(fromDate);
        assertThat(context.toDate()).isEqualTo(toDate);
    }

    @Test
    @DisplayName("Should use raw rates not adjusted rates in context")
    void shouldUseRawRatesInContext() {
        // Given: raw and adjusted rates are different
        List<HistoricalRateEntry> mockHistory = List.of(
                new HistoricalRateEntry(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("4.5"),     // raw
                        new BigDecimal("4.37")     // adjusted (with spread)
                ),
                new HistoricalRateEntry(
                        LocalDate.of(2024, 2, 2),
                        new BigDecimal("4.6"),
                        new BigDecimal("4.47")
                )
        );
        
        when(exchangeHistoryService.getHistoricalRates(any(), any(), any(), any()))
                .thenReturn(mockHistory);
        
        when(trendInsightGenerator.generateInsight(any()))
                .thenReturn("Test insight");

        // When
        service.generateTrendInsight(
                "EUR", "PLN",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5)
        );

        // Then
        verify(trendInsightGenerator).generateInsight(contextCaptor.capture());
        
        TrendInsightContext context = contextCaptor.getValue();
        // Should use raw rates, not adjusted
        assertThat(context.historicalRates().get(0).rawRate())
                .isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(context.historicalRates().get(1).rawRate())
                .isEqualByComparingTo(new BigDecimal("4.6"));
    }

    @Test
    @DisplayName("Insight request should NOT call usage tracking")
    void insightRequestShouldNotCallUsageTracking() {
        // Given
        when(exchangeHistoryService.getHistoricalRates(any(), any(), any(), any()))
                .thenReturn(createMockHistory());
        
        when(trendInsightGenerator.generateInsight(any()))
                .thenReturn("Test insight");

        // When
        service.generateTrendInsight(
                "EUR", "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5)
        );

        // Then
        // Only historical data fetch should occur, no usage tracking
        verify(exchangeHistoryService, times(1)).getHistoricalRates(any(), any(), any(), any());
        verifyNoMoreInteractions(exchangeHistoryService);
    }

    private List<HistoricalRateEntry> createMockHistory() {
        return List.of(
                new HistoricalRateEntry(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("0.85"),
                        new BigDecimal("0.83")
                ),
                new HistoricalRateEntry(
                        LocalDate.of(2024, 2, 5),
                        new BigDecimal("0.86"),
                        new BigDecimal("0.84")
                )
        );
    }
}
