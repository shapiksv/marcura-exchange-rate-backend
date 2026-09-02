package com.example.marcuraexchangeratebackend.currency.api;

import com.example.marcuraexchangeratebackend.common.error.RateNotFoundException;
import com.example.marcuraexchangeratebackend.currency.application.CurrencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for CurrencyController.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyController Tests")
class CurrencyControllerTest {

    @Mock
    private CurrencyService currencyService;

    @InjectMocks
    private CurrencyController controller;

    @Test
    @DisplayName("Should return list of currencies when rates exist")
    void shouldReturnCurrenciesWhenRatesExist() {
        // Given
        List<String> mockCurrencies = List.of("AED", "EUR", "PLN", "USD");
        when(currencyService.getAvailableCurrencies()).thenReturn(mockCurrencies);

        // When
        ResponseEntity<CurrenciesResponse> response = controller.getAvailableCurrencies();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currencies()).hasSize(4);
        assertThat(response.getBody().currencies()).containsExactly("AED", "EUR", "PLN", "USD");
        
        verify(currencyService).getAvailableCurrencies();
    }

    @Test
    @DisplayName("Should return empty list when no currencies available")
    void shouldReturnEmptyListWhenNoCurrencies() {
        // Given
        when(currencyService.getAvailableCurrencies()).thenReturn(List.of());

        // When
        ResponseEntity<CurrenciesResponse> response = controller.getAvailableCurrencies();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currencies()).isEmpty();
        
        verify(currencyService).getAvailableCurrencies();
    }

    @Test
    @DisplayName("Should propagate RateNotFoundException when no rates in database")
    void shouldPropagateRateNotFoundExceptionWhenNoRates() {
        // Given
        when(currencyService.getAvailableCurrencies())
                .thenThrow(new RateNotFoundException("No exchange rates available"));

        // When & Then
        assertThatThrownBy(() -> controller.getAvailableCurrencies())
                .isInstanceOf(RateNotFoundException.class)
                .hasMessageContaining("No exchange rates available");
        
        verify(currencyService).getAvailableCurrencies();
    }

    @Test
    @DisplayName("Should return currencies sorted alphabetically")
    void shouldReturnCurrenciesSortedAlphabetically() {
        // Given: Service returns sorted list
        List<String> sortedCurrencies = List.of("AED", "EUR", "GBP", "PLN", "USD");
        when(currencyService.getAvailableCurrencies()).thenReturn(sortedCurrencies);

        // When
        ResponseEntity<CurrenciesResponse> response = controller.getAvailableCurrencies();

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currencies())
                .containsExactly("AED", "EUR", "GBP", "PLN", "USD")
                .isSorted();
        
        verify(currencyService).getAvailableCurrencies();
    }

    @Test
    @DisplayName("Should include base currency in the list")
    void shouldIncludeBaseCurrencyInList() {
        // Given: EUR is base currency and also appears as target
        List<String> currencies = List.of("EUR", "PLN", "USD");
        when(currencyService.getAvailableCurrencies()).thenReturn(currencies);

        // When
        ResponseEntity<CurrenciesResponse> response = controller.getAvailableCurrencies();

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currencies()).contains("EUR");
        
        verify(currencyService).getAvailableCurrencies();
    }
}
