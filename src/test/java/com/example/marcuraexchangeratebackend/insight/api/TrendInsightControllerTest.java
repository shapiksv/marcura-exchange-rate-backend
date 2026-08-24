package com.example.marcuraexchangeratebackend.insight.api;

import com.example.marcuraexchangeratebackend.common.error.RateNotFoundException;
import com.example.marcuraexchangeratebackend.insight.application.TrendInsightService;
import com.example.marcuraexchangeratebackend.insight.domain.AiProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TrendInsightController.
 * <p>
 * Tests controller as POJO without Spring MVC infrastructure.
 */
@ExtendWith(MockitoExtension.class)
class TrendInsightControllerTest {

    @Mock
    private TrendInsightService trendInsightService;

    private TrendInsightController controller;

    @BeforeEach
    void setUp() {
        controller = new TrendInsightController(trendInsightService);
    }

    @Test
    @DisplayName("Should return 200 with insight for valid request")
    void shouldReturn200WithInsightForValidRequest() {
        // Given
        String mockInsight = "EUR/GBP declined by approximately 1.8% over the selected period.";
        
        when(trendInsightService.generateTrendInsight(
                eq("EUR"),
                eq("GBP"),
                eq(LocalDate.of(2024, 2, 1)),
                eq(LocalDate.of(2024, 3, 1))
        )).thenReturn(mockInsight);

        // When
        ResponseEntity<TrendInsightResponse> response = controller.getTrendInsight(
                "EUR",
                "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 3, 1)
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().from()).isEqualTo("EUR");
        assertThat(response.getBody().to()).isEqualTo("GBP");
        assertThat(response.getBody().fromDate()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(response.getBody().toDate()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(response.getBody().insight()).isEqualTo(mockInsight);

        verify(trendInsightService).generateTrendInsight(
                "EUR", "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 3, 1)
        );
    }

    @Test
    @DisplayName("Should preserve currency pair in response")
    void shouldPreserveCurrencyPairInResponse() {
        // Given
        when(trendInsightService.generateTrendInsight(
                eq("USD"), eq("JPY"), eq(LocalDate.of(2024, 3, 1)), eq(LocalDate.of(2024, 3, 31))
        )).thenReturn("Test insight");

        // When
        ResponseEntity<TrendInsightResponse> response = controller.getTrendInsight(
                "USD", "JPY",
                LocalDate.of(2024, 3, 1),
                LocalDate.of(2024, 3, 31)
        );

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().from()).isEqualTo("USD");
        assertThat(response.getBody().to()).isEqualTo("JPY");
    }

    @Test
    @DisplayName("Should preserve date range in response")
    void shouldPreserveDateRangeInResponse() {
        // Given
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 1, 31);
        
        when(trendInsightService.generateTrendInsight(
                eq("EUR"), eq("USD"), eq(fromDate), eq(toDate)
        )).thenReturn("Test insight");

        // When
        ResponseEntity<TrendInsightResponse> response = controller.getTrendInsight(
                "EUR", "USD", fromDate, toDate
        );

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fromDate()).isEqualTo(fromDate);
        assertThat(response.getBody().toDate()).isEqualTo(toDate);
    }

    @Test
    @DisplayName("Should include insight text in response")
    void shouldIncludeInsightTextInResponse() {
        // Given
        String expectedInsight = "EUR/USD remained stable with minimal fluctuation throughout February.";
        
        when(trendInsightService.generateTrendInsight(
                eq("EUR"), eq("USD"), eq(LocalDate.of(2024, 2, 1)), eq(LocalDate.of(2024, 2, 29))
        )).thenReturn(expectedInsight);

        // When
        ResponseEntity<TrendInsightResponse> response = controller.getTrendInsight(
                "EUR", "USD",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 29)
        );

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().insight()).isEqualTo(expectedInsight);
    }

    @Test
    @DisplayName("Should propagate RateNotFoundException when no data exists")
    void shouldPropagateRateNotFoundException() {
        // Given
        when(trendInsightService.generateTrendInsight(
                eq("EUR"), eq("XXX"), eq(LocalDate.of(2024, 2, 1)), eq(LocalDate.of(2024, 2, 5))
        )).thenThrow(new RateNotFoundException("No historical rates available"));

        // When & Then
        assertThatThrownBy(() ->
                controller.getTrendInsight(
                        "EUR", "XXX",
                        LocalDate.of(2024, 2, 1),
                        LocalDate.of(2024, 2, 5)
                )
        ).isInstanceOf(RateNotFoundException.class);
    }

    @Test
    @DisplayName("Should propagate AiProviderException when AI fails")
    void shouldPropagateAiProviderException() {
        // Given
        when(trendInsightService.generateTrendInsight(
                eq("EUR"), eq("GBP"), eq(LocalDate.of(2024, 2, 1)), eq(LocalDate.of(2024, 2, 5))
        )).thenThrow(new AiProviderException("Ollama unavailable"));

        // When & Then
        assertThatThrownBy(() ->
                controller.getTrendInsight(
                        "EUR", "GBP",
                        LocalDate.of(2024, 2, 1),
                        LocalDate.of(2024, 2, 5)
                )
        ).isInstanceOf(AiProviderException.class);
    }

    @Test
    @DisplayName("Should handle deterministic insufficient data message")
    void shouldHandleDeterministicInsufficientDataMessage() {
        // Given
        String insufficientDataMessage = "Insufficient historical data to determine a trend for the selected period.";
        
        when(trendInsightService.generateTrendInsight(
                eq("EUR"), eq("GBP"), eq(LocalDate.of(2024, 2, 1)), eq(LocalDate.of(2024, 2, 2))
        )).thenReturn(insufficientDataMessage);

        // When
        ResponseEntity<TrendInsightResponse> response = controller.getTrendInsight(
                "EUR", "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 2)
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().insight()).isEqualTo(insufficientDataMessage);
    }

    @Test
    @DisplayName("Should delegate all parameters to service correctly")
    void shouldDelegateAllParametersToServiceCorrectly() {
        // Given
        String from = "CHF";
        String to = "NOK";
        LocalDate fromDate = LocalDate.of(2024, 4, 1);
        LocalDate toDate = LocalDate.of(2024, 4, 30);
        
        when(trendInsightService.generateTrendInsight(
                eq(from), eq(to), eq(fromDate), eq(toDate)
        )).thenReturn("Test insight");

        // When
        controller.getTrendInsight(from, to, fromDate, toDate);

        // Then
        verify(trendInsightService).generateTrendInsight(from, to, fromDate, toDate);
    }
}
