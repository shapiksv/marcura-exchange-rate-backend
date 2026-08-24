package com.example.marcuraexchangeratebackend.exchange.application;

import com.example.marcuraexchangeratebackend.common.error.InvalidRequestException;
import com.example.marcuraexchangeratebackend.exchange.api.HistoricalRateEntry;
import com.example.marcuraexchangeratebackend.exchange.domain.ExchangeRateCalculator;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateRepository;
import com.example.marcuraexchangeratebackend.rate.persistence.HistoricalRateProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Unit tests for ExchangeHistoryService.
 * <p>
 * Tests the optimized single-query historical rates logic.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeHistoryServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private ExchangeRateCalculator calculator;

    private ExchangeHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeHistoryService(exchangeRateRepository, calculator);
    }

    @Test
    @DisplayName("Should return historical rates for valid date range using single query")
    void shouldReturnHistoricalRatesForValidDateRange() {
        // Given
        LocalDate fromDate = LocalDate.of(2024, 3, 1);
        LocalDate toDate = LocalDate.of(2024, 3, 3);

        List<HistoricalRateProjection> projections = List.of(
                new HistoricalRateProjection(
                        LocalDate.of(2024, 3, 1), "EUR",
                        new BigDecimal("4.50"), new BigDecimal("1.00")
                ),
                new HistoricalRateProjection(
                        LocalDate.of(2024, 3, 3), "EUR",
                        new BigDecimal("4.55"), new BigDecimal("1.00")
                )
        );

        when(exchangeRateRepository.findHistoricalRatesInSingleQuery(
                eq("EUR"), eq("PLN"), eq(fromDate), eq(toDate)))
                .thenReturn(projections);

        when(calculator.calculateRawCrossRate(any(), any()))
                .thenReturn(new BigDecimal("4.2"));
        when(calculator.calculateAdjustedRate(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("4.1"));

        // When
        List<HistoricalRateEntry> result = service.getHistoricalRates("EUR", "PLN", fromDate, toDate);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2024, 3, 3));

        verify(exchangeRateRepository).findHistoricalRatesInSingleQuery("EUR", "PLN", fromDate, toDate);
        // No additional queries to findByRateDateAndBaseCurrencyAndCurrencyCode
        verify(exchangeRateRepository, never()).findByRateDateAndBaseCurrencyAndCurrencyCode(any(), any(), any());
    }

    @Test
    @DisplayName("Should return empty list when no snapshots exist in date range")
    void shouldReturnEmptyListWhenNoSnapshotsExist() {
        // Given
        LocalDate fromDate = LocalDate.of(2024, 3, 1);
        LocalDate toDate = LocalDate.of(2024, 3, 10);

        when(exchangeRateRepository.findHistoricalRatesInSingleQuery(
                eq("USD"), eq("GBP"), eq(fromDate), eq(toDate)))
                .thenReturn(Collections.emptyList());

        // When
        List<HistoricalRateEntry> result = service.getHistoricalRates("USD", "GBP", fromDate, toDate);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle same-currency historical query using single query")
    void shouldHandleSameCurrencyHistoricalQuery() {
        // Given
        LocalDate fromDate = LocalDate.of(2024, 3, 1);
        LocalDate toDate = LocalDate.of(2024, 3, 3);

        List<HistoricalRateProjection> projections = List.of(
                new HistoricalRateProjection(
                        LocalDate.of(2024, 3, 1), "EUR",
                        new BigDecimal("1.00"), new BigDecimal("1.00")
                ),
                new HistoricalRateProjection(
                        LocalDate.of(2024, 3, 3), "EUR",
                        new BigDecimal("1.00"), new BigDecimal("1.00")
                )
        );

        when(exchangeRateRepository.findHistoricalRatesInSingleQuery(
                eq("EUR"), eq("EUR"), eq(fromDate), eq(toDate)))
                .thenReturn(projections);

        // When
        List<HistoricalRateEntry> result = service.getHistoricalRates("EUR", "EUR", fromDate, toDate);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).rawRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.get(0).adjustedRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.get(1).rawRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.get(1).adjustedRate()).isEqualByComparingTo(BigDecimal.ONE);

        verify(exchangeRateRepository).findHistoricalRatesInSingleQuery("EUR", "EUR", fromDate, toDate);
    }

    @Test
    @DisplayName("Should normalize currency codes to uppercase")
    void shouldNormalizeCurrencyCodesToUppercase() {
        // Given
        LocalDate fromDate = LocalDate.of(2024, 3, 1);
        LocalDate toDate = LocalDate.of(2024, 3, 5);

        when(exchangeRateRepository.findHistoricalRatesInSingleQuery(
                any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // When
        service.getHistoricalRates("eur", "usd", fromDate, toDate);

        // Then
        verify(exchangeRateRepository).findHistoricalRatesInSingleQuery(
                eq("EUR"), eq("USD"), eq(fromDate), eq(toDate));
    }

    @Test
    @DisplayName("Should throw InvalidRequestException when fromDate is after toDate")
    void shouldThrowExceptionWhenFromDateAfterToDate() {
        // Given
        LocalDate fromDate = LocalDate.of(2024, 3, 10);
        LocalDate toDate = LocalDate.of(2024, 3, 1);

        // When & Then
        assertThatThrownBy(() -> service.getHistoricalRates("EUR", "USD", fromDate, toDate))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid date range");

        verifyNoInteractions(exchangeRateRepository);
        verifyNoInteractions(calculator);
    }

    @Test
    @DisplayName("Should calculate both raw and adjusted rates from projection")
    void shouldCalculateBothRawAndAdjustedRates() {
        // Given
        LocalDate fromDate = LocalDate.of(2024, 3, 15);
        LocalDate toDate = LocalDate.of(2024, 3, 15);

        List<HistoricalRateProjection> projections = List.of(
                new HistoricalRateProjection(
                        LocalDate.of(2024, 3, 15), "EUR",
                        new BigDecimal("4.5678"), new BigDecimal("1.0234")
                )
        );

        when(exchangeRateRepository.findHistoricalRatesInSingleQuery(
                eq("EUR"), eq("PLN"), eq(fromDate), eq(toDate)))
                .thenReturn(projections);

        BigDecimal expectedRaw = new BigDecimal("4.456789");
        BigDecimal expectedAdjusted = new BigDecimal("4.334567");

        when(calculator.calculateRawCrossRate(
                eq(new BigDecimal("4.5678")), eq(new BigDecimal("1.0234"))))
                .thenReturn(expectedRaw);
        when(calculator.calculateAdjustedRate(
                eq(new BigDecimal("4.5678")), eq(new BigDecimal("1.0234")),
                eq("EUR"), eq("EUR"), eq("PLN")))
                .thenReturn(expectedAdjusted);

        // When
        List<HistoricalRateEntry> result = service.getHistoricalRates("EUR", "PLN", fromDate, toDate);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).rawRate()).isEqualByComparingTo(expectedRaw);
        assertThat(result.get(0).adjustedRate()).isEqualByComparingTo(expectedAdjusted);
    }

    @Test
    @DisplayName("Should omit missing dates in range")
    void shouldOmitMissingDatesInRange() {
        // Given: range is 2024-03-01 to 2024-03-05, but only 03-01 and 03-05 exist
        LocalDate fromDate = LocalDate.of(2024, 3, 1);
        LocalDate toDate = LocalDate.of(2024, 3, 5);

        List<HistoricalRateProjection> projections = List.of(
                new HistoricalRateProjection(
                        LocalDate.of(2024, 3, 1), "EUR",
                        new BigDecimal("1.10"), new BigDecimal("1.00")
                ),
                new HistoricalRateProjection(
                        LocalDate.of(2024, 3, 5), "EUR",
                        new BigDecimal("1.12"), new BigDecimal("1.00")
                )
        );

        when(exchangeRateRepository.findHistoricalRatesInSingleQuery(
                eq("USD"), eq("EUR"), eq(fromDate), eq(toDate)))
                .thenReturn(projections);

        when(calculator.calculateRawCrossRate(any(), any()))
                .thenReturn(new BigDecimal("0.9"));
        when(calculator.calculateAdjustedRate(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("0.87"));

        // When
        List<HistoricalRateEntry> result = service.getHistoricalRates("USD", "EUR", fromDate, toDate);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2024, 3, 5));
        // 03-02, 03-03, 03-04 are NOT present
    }
}
