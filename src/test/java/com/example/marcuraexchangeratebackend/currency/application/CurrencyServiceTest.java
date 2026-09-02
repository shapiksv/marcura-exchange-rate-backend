package com.example.marcuraexchangeratebackend.currency.application;

import com.example.marcuraexchangeratebackend.common.error.RateNotFoundException;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for CurrencyService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyService Tests")
class CurrencyServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private CurrencyService service;

    @Test
    @DisplayName("Should return currencies from latest snapshot")
    void shouldReturnCurrenciesFromLatestSnapshot() {
        // Given
        List<String> mockCurrencies = List.of("AED", "EUR", "PLN", "USD");
        
        when(exchangeRateRepository.findDistinctCurrenciesFromLatestSnapshot())
                .thenReturn(mockCurrencies);

        // When
        List<String> result = service.getAvailableCurrencies();

        // Then
        assertThat(result).hasSize(4);
        assertThat(result).containsExactly("AED", "EUR", "PLN", "USD");
        
        verify(exchangeRateRepository).findDistinctCurrenciesFromLatestSnapshot();
    }

    @Test
    @DisplayName("Should throw RateNotFoundException when no rates exist")
    void shouldThrowExceptionWhenNoRatesExist() {
        // Given
        when(exchangeRateRepository.findDistinctCurrenciesFromLatestSnapshot())
                .thenReturn(List.of());

        // When & Then
        assertThatThrownBy(() -> service.getAvailableCurrencies())
                .isInstanceOf(RateNotFoundException.class)
                .hasMessageContaining("No exchange rates available");
        
        verify(exchangeRateRepository).findDistinctCurrenciesFromLatestSnapshot();
    }

    @Test
    @DisplayName("Should return currencies sorted alphabetically")
    void shouldReturnCurrenciesSortedAlphabetically() {
        // Given
        List<String> sortedCurrencies = List.of("AED", "EUR", "GBP", "PLN", "USD");
        
        when(exchangeRateRepository.findDistinctCurrenciesFromLatestSnapshot())
                .thenReturn(sortedCurrencies);

        // When
        List<String> result = service.getAvailableCurrencies();

        // Then
        assertThat(result).isSorted();
        assertThat(result).containsExactly("AED", "EUR", "GBP", "PLN", "USD");
        
        verify(exchangeRateRepository).findDistinctCurrenciesFromLatestSnapshot();
    }

    @Test
    @DisplayName("Should include both target and base currencies")
    void shouldIncludeBothTargetAndBaseCurrencies() {
        // Given: Repository query returns both target currencies and base currency
        List<String> allCurrencies = List.of("EUR", "PLN", "USD"); // EUR might be base
        
        when(exchangeRateRepository.findDistinctCurrenciesFromLatestSnapshot())
                .thenReturn(allCurrencies);

        // When
        List<String> result = service.getAvailableCurrencies();

        // Then
        assertThat(result).contains("EUR", "PLN", "USD");
        
        verify(exchangeRateRepository).findDistinctCurrenciesFromLatestSnapshot();
    }
}
