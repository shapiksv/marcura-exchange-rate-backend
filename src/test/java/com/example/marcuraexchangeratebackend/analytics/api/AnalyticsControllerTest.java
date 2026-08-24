package com.example.marcuraexchangeratebackend.analytics.api;

import com.example.marcuraexchangeratebackend.analytics.application.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AnalyticsController.
 * <p>
 * Tests controller as plain Java object without Spring MVC infrastructure.
 * HTTP layer semantics (parameter validation, JSON serialization) are tested via integration tests.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;

    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsController(analyticsService);
    }

    @Test
    @DisplayName("Should return 200 with analytics data when data exists")
    void shouldReturn200WithAnalyticsData() {
        // Given
        List<CurrencyUsageSummary> topCurrencies = List.of(
                new CurrencyUsageSummary("EUR", 142L, toOffsetDateTime(LocalDate.of(2024, 3, 15))),
                new CurrencyUsageSummary("USD", 98L, toOffsetDateTime(LocalDate.of(2024, 3, 14)))
        );

        List<DailyUsageEntry> dailyUsage = List.of(
                new DailyUsageEntry(LocalDate.of(2024, 3, 15), "EUR", 12L),
                new DailyUsageEntry(LocalDate.of(2024, 3, 15), "USD", 9L)
        );

        when(analyticsService.getTopCurrencies()).thenReturn(topCurrencies);
        when(analyticsService.getDailyUsage()).thenReturn(dailyUsage);

        // When
        ResponseEntity<AnalyticsResponse> response = controller.getAnalytics();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().topCurrencies()).hasSize(2);
        assertThat(response.getBody().topCurrencies().get(0).currency()).isEqualTo("EUR");
        assertThat(response.getBody().topCurrencies().get(0).totalCount()).isEqualTo(142L);
        assertThat(response.getBody().topCurrencies().get(1).currency()).isEqualTo("USD");
        assertThat(response.getBody().topCurrencies().get(1).totalCount()).isEqualTo(98L);
        assertThat(response.getBody().dailyUsage()).hasSize(2);
        assertThat(response.getBody().dailyUsage().get(0).currency()).isEqualTo("EUR");
        assertThat(response.getBody().dailyUsage().get(0).count()).isEqualTo(12L);

        verify(analyticsService).getTopCurrencies();
        verify(analyticsService).getDailyUsage();
    }

    @Test
    @DisplayName("Should return 200 with empty arrays when no usage data exists")
    void shouldReturn200WithEmptyArraysWhenNoData() {
        // Given
        when(analyticsService.getTopCurrencies()).thenReturn(Collections.emptyList());
        when(analyticsService.getDailyUsage()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<AnalyticsResponse> response = controller.getAnalytics();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().topCurrencies()).isEmpty();
        assertThat(response.getBody().dailyUsage()).isEmpty();

        verify(analyticsService).getTopCurrencies();
        verify(analyticsService).getDailyUsage();
    }

    @Test
    @DisplayName("Should include topCurrencies in response")
    void shouldIncludeTopCurrenciesInResponse() {
        // Given
        List<CurrencyUsageSummary> topCurrencies = List.of(
                new CurrencyUsageSummary("GBP", 200L, toOffsetDateTime(LocalDate.of(2024, 3, 20)))
        );

        when(analyticsService.getTopCurrencies()).thenReturn(topCurrencies);
        when(analyticsService.getDailyUsage()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<AnalyticsResponse> response = controller.getAnalytics();

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().topCurrencies()).hasSize(1);
        assertThat(response.getBody().topCurrencies().get(0).currency()).isEqualTo("GBP");
        assertThat(response.getBody().topCurrencies().get(0).totalCount()).isEqualTo(200L);
    }

    @Test
    @DisplayName("Should include dailyUsage in response")
    void shouldIncludeDailyUsageInResponse() {
        // Given
        List<DailyUsageEntry> dailyUsage = List.of(
                new DailyUsageEntry(LocalDate.of(2024, 3, 10), "JPY", 25L)
        );

        when(analyticsService.getTopCurrencies()).thenReturn(Collections.emptyList());
        when(analyticsService.getDailyUsage()).thenReturn(dailyUsage);

        // When
        ResponseEntity<AnalyticsResponse> response = controller.getAnalytics();

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().dailyUsage()).hasSize(1);
        assertThat(response.getBody().dailyUsage().get(0).currency()).isEqualTo("JPY");
        assertThat(response.getBody().dailyUsage().get(0).count()).isEqualTo(25L);
    }

    @Test
    @DisplayName("Should handle large datasets")
    void shouldHandleLargeDatasets() {
        // Given
        List<CurrencyUsageSummary> largeCurrencyList = List.of(
                new CurrencyUsageSummary("EUR", 1000L, toOffsetDateTime(LocalDate.of(2024, 3, 20))),
                new CurrencyUsageSummary("USD", 950L, toOffsetDateTime(LocalDate.of(2024, 3, 20))),
                new CurrencyUsageSummary("GBP", 800L, toOffsetDateTime(LocalDate.of(2024, 3, 19))),
                new CurrencyUsageSummary("JPY", 750L, toOffsetDateTime(LocalDate.of(2024, 3, 19))),
                new CurrencyUsageSummary("CHF", 500L, toOffsetDateTime(LocalDate.of(2024, 3, 18)))
        );

        when(analyticsService.getTopCurrencies()).thenReturn(largeCurrencyList);
        when(analyticsService.getDailyUsage()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<AnalyticsResponse> response = controller.getAnalytics();

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().topCurrencies()).hasSize(5);
    }

    private OffsetDateTime toOffsetDateTime(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
