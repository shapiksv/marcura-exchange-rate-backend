package com.example.marcuraexchangeratebackend.exchange.api;

import com.example.marcuraexchangeratebackend.common.error.InvalidRequestException;
import com.example.marcuraexchangeratebackend.exchange.application.ExchangeHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ExchangeHistoryController.
 * <p>
 * Tests controller as plain Java object without Spring MVC infrastructure.
 * HTTP layer semantics (parameter validation, JSON serialization) are tested via integration tests.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeHistoryControllerTest {

    @Mock
    private ExchangeHistoryService exchangeHistoryService;

    private ExchangeHistoryController controller;

    @BeforeEach
    void setUp() {
        controller = new ExchangeHistoryController(exchangeHistoryService);
    }

    @Test
    @DisplayName("Should return 200 and historical rates for valid request")
    void shouldReturn200ForValidRequest() {
        // Given
        List<HistoricalRateEntry> mockEntries = List.of(
                new HistoricalRateEntry(
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("0.861234"),
                        new BigDecimal("0.837543")
                ),
                new HistoricalRateEntry(
                        LocalDate.of(2024, 2, 2),
                        new BigDecimal("0.865678"),
                        new BigDecimal("0.841789")
                )
        );

        when(exchangeHistoryService.getHistoricalRates(
                eq("EUR"),
                eq("GBP"),
                eq(LocalDate.of(2024, 2, 1)),
                eq(LocalDate.of(2024, 2, 28))
        )).thenReturn(mockEntries);

        // When
        ResponseEntity<ExchangeHistoryResponse> response = controller.getHistoricalRates(
                "EUR", "GBP",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 28)
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().from()).isEqualTo("EUR");
        assertThat(response.getBody().to()).isEqualTo("GBP");
        assertThat(response.getBody().fromDate()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(response.getBody().toDate()).isEqualTo(LocalDate.of(2024, 2, 28));
        assertThat(response.getBody().rates()).hasSize(2);
        assertThat(response.getBody().rates().get(0).date()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(response.getBody().rates().get(0).rawRate()).isEqualByComparingTo(new BigDecimal("0.861234"));
        assertThat(response.getBody().rates().get(0).adjustedRate()).isEqualByComparingTo(new BigDecimal("0.837543"));

        verify(exchangeHistoryService).getHistoricalRates("EUR", "GBP",
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 28));
    }

    @Test
    @DisplayName("Should return 200 with empty rates array when no data available")
    void shouldReturn200WithEmptyRatesWhenNoData() {
        // Given
        when(exchangeHistoryService.getHistoricalRates(
                any(), any(), any(), any()
        )).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<ExchangeHistoryResponse> response = controller.getHistoricalRates(
                "EUR", "XXX",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 28)
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().rates()).isEmpty();
    }

    @Test
    @DisplayName("Should propagate InvalidRequestException when service throws (fromDate > toDate)")
    void shouldPropagateInvalidRequestException() {
        // Given
        when(exchangeHistoryService.getHistoricalRates(
                any(), any(), any(), any()
        )).thenThrow(new InvalidRequestException("Invalid date range: fromDate must not be after toDate"));

        // When & Then
        assertThatThrownBy(() ->
                controller.getHistoricalRates(
                        "EUR", "GBP",
                        LocalDate.of(2024, 3, 1),
                        LocalDate.of(2024, 2, 1)
                )
        ).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid date range");
    }

    @Test
    @DisplayName("Should include both rawRate and adjustedRate in response")
    void shouldIncludeBothRawAndAdjustedRates() {
        // Given
        List<HistoricalRateEntry> mockEntries = List.of(
                new HistoricalRateEntry(
                        LocalDate.of(2024, 3, 15),
                        new BigDecimal("4.456789"),  // raw
                        new BigDecimal("4.334567")   // adjusted
                )
        );

        when(exchangeHistoryService.getHistoricalRates(
                any(), any(), any(), any()
        )).thenReturn(mockEntries);

        // When
        ResponseEntity<ExchangeHistoryResponse> response = controller.getHistoricalRates(
                "EUR", "PLN",
                LocalDate.of(2024, 3, 15),
                LocalDate.of(2024, 3, 15)
        );

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().rates()).hasSize(1);
        assertThat(response.getBody().rates().get(0).rawRate()).isEqualByComparingTo(new BigDecimal("4.456789"));
        assertThat(response.getBody().rates().get(0).adjustedRate()).isEqualByComparingTo(new BigDecimal("4.334567"));
    }

    @Test
    @DisplayName("Should handle same-currency request")
    void shouldHandleSameCurrencyRequest() {
        // Given
        List<HistoricalRateEntry> mockEntries = List.of(
                new HistoricalRateEntry(
                        LocalDate.of(2024, 3, 15),
                        BigDecimal.ONE,
                        BigDecimal.ONE
                )
        );

        when(exchangeHistoryService.getHistoricalRates(
                eq("EUR"), eq("EUR"), any(), any()
        )).thenReturn(mockEntries);

        // When
        ResponseEntity<ExchangeHistoryResponse> response = controller.getHistoricalRates(
                "EUR", "EUR",
                LocalDate.of(2024, 3, 15),
                LocalDate.of(2024, 3, 15)
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().rates()).hasSize(1);
        assertThat(response.getBody().rates().get(0).rawRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.getBody().rates().get(0).adjustedRate()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("Should preserve date range in response")
    void shouldPreserveDateRangeInResponse() {
        // Given
        when(exchangeHistoryService.getHistoricalRates(
                any(), any(), any(), any()
        )).thenReturn(Collections.emptyList());

        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 1, 31);

        // When
        ResponseEntity<ExchangeHistoryResponse> response = controller.getHistoricalRates(
                "USD", "GBP", fromDate, toDate
        );

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fromDate()).isEqualTo(fromDate);
        assertThat(response.getBody().toDate()).isEqualTo(toDate);
    }

    @Test
    @DisplayName("Should preserve currency codes in response")
    void shouldPreserveCurrencyCodesInResponse() {
        // Given
        when(exchangeHistoryService.getHistoricalRates(
                any(), any(), any(), any()
        )).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<ExchangeHistoryResponse> response = controller.getHistoricalRates(
                "JPY", "KRW",
                LocalDate.of(2024, 3, 1),
                LocalDate.of(2024, 3, 31)
        );

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().from()).isEqualTo("JPY");
        assertThat(response.getBody().to()).isEqualTo("KRW");
    }
}
