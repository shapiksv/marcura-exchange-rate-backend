package com.example.marcuraexchangeratebackend.exchange.application;

import com.example.marcuraexchangeratebackend.analytics.persistence.CurrencyUsageDailyRepository;
import com.example.marcuraexchangeratebackend.common.error.RateNotFoundException;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateEntity;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExchangeApplicationService.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeApplicationServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private CurrencyUsageDailyRepository usageRepository;

    private ExchangeApplicationService service;

    private static final LocalDate TEST_DATE = LocalDate.of(2024, 3, 15);
    private static final String BASE_CURRENCY = "EUR";

    @BeforeEach
    void setUp() {
        service = new ExchangeApplicationService(exchangeRateRepository, usageRepository);
    }

    @Test
    void calculateExchange_withExplicitDate_success() {
        // Arrange
        ExchangeRateEntity fromRate = createRateEntity("EUR", BigDecimal.ONE);
        ExchangeRateEntity toRate = createRateEntity("PLN", new BigDecimal("4.56734"));

        // Mock snapshot resolution
        when(exchangeRateRepository.findCommonSnapshotForDate(TEST_DATE, "EUR", "PLN"))
                .thenReturn(Optional.of(new Object[]{java.sql.Date.valueOf(TEST_DATE), BASE_CURRENCY}));

        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(TEST_DATE, BASE_CURRENCY, "EUR"))
                .thenReturn(Optional.of(fromRate));
        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(TEST_DATE, BASE_CURRENCY, "PLN"))
                .thenReturn(Optional.of(toRate));

        when(usageRepository.incrementUsageAtomic(any(), any(), any())).thenReturn(1);
        when(usageRepository.getTotalQueryCountForCurrency("EUR")).thenReturn(142L);
        when(usageRepository.getTotalQueryCountForCurrency("PLN")).thenReturn(37L);

        // Act
        ExchangeResult result = service.calculateExchange("EUR", "PLN", TEST_DATE);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.from()).isEqualTo("EUR");
        assertThat(result.to()).isEqualTo("PLN");
        assertThat(result.date()).isEqualTo(TEST_DATE);
        assertThat(result.exchange()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.fromQueryCount()).isEqualTo(142L);
        assertThat(result.toQueryCount()).isEqualTo(37L);

        // Verify usage increments
        verify(usageRepository, times(2)).incrementUsageAtomic(any(), any(), any());
    }

    @Test
    void calculateExchange_withLatestDate_success() {
        // Arrange
        ExchangeRateEntity fromRate = createRateEntity("USD", new BigDecimal("1.0836"));
        ExchangeRateEntity toRate = createRateEntity("GBP", new BigDecimal("0.8573"));

        // Mock latest common snapshot resolution
        when(exchangeRateRepository.findLatestCommonSnapshot("USD", "GBP"))
                .thenReturn(Optional.of(new Object[]{java.sql.Date.valueOf(TEST_DATE), BASE_CURRENCY}));

        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(TEST_DATE, BASE_CURRENCY, "USD"))
                .thenReturn(Optional.of(fromRate));
        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(TEST_DATE, BASE_CURRENCY, "GBP"))
                .thenReturn(Optional.of(toRate));

        when(usageRepository.incrementUsageAtomic(any(), any(), any())).thenReturn(1);
        when(usageRepository.getTotalQueryCountForCurrency("USD")).thenReturn(50L);
        when(usageRepository.getTotalQueryCountForCurrency("GBP")).thenReturn(30L);

        // Act
        ExchangeResult result = service.calculateExchange("USD", "GBP", null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.from()).isEqualTo("USD");
        assertThat(result.to()).isEqualTo("GBP");
        assertThat(result.date()).isEqualTo(TEST_DATE);
        assertThat(result.exchange()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void calculateExchange_sameCurrency_incrementsTwice() {
        // Arrange
        ExchangeRateEntity eurRate = createRateEntity("EUR", BigDecimal.ONE);

        // For same currency, query uses EUR for both from and to
        when(exchangeRateRepository.findCommonSnapshotForDate(TEST_DATE, "EUR", "EUR"))
                .thenReturn(Optional.of(new Object[]{java.sql.Date.valueOf(TEST_DATE), BASE_CURRENCY}));

        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(TEST_DATE, BASE_CURRENCY, "EUR"))
                .thenReturn(Optional.of(eurRate));

        when(usageRepository.incrementUsageAtomic(any(), any(), any())).thenReturn(1);
        when(usageRepository.getTotalQueryCountForCurrency("EUR")).thenReturn(2L);

        // Act
        ExchangeResult result = service.calculateExchange("EUR", "EUR", TEST_DATE);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.from()).isEqualTo("EUR");
        assertThat(result.to()).isEqualTo("EUR");
        assertThat(result.exchange()).isEqualByComparingTo(BigDecimal.ONE);

        // Verify EUR usage incremented twice (once for from, once for to)
        verify(usageRepository, times(2)).incrementUsageAtomic(eq("EUR"), any(LocalDate.class), any(OffsetDateTime.class));
    }

    @Test
    void calculateExchange_noRatesAvailable_throwsException() {
        // Arrange
        when(exchangeRateRepository.findCommonSnapshotForDate(TEST_DATE, "EUR", "PLN"))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findByRateDateAndBaseCurrency(TEST_DATE, null))
                .thenReturn(List.of());

        // Act & Assert
        assertThatThrownBy(() -> service.calculateExchange("EUR", "PLN", TEST_DATE))
                .isInstanceOf(RateNotFoundException.class)
                .hasMessageContaining("No rates available for date");

        // Verify no usage increments on failure
        verify(usageRepository, never()).incrementUsageAtomic(any(), any(), any());
    }

    @Test
    void calculateExchange_fromCurrencyMissing_throwsException() {
        // Arrange
        ExchangeRateEntity toRate = createRateEntity("PLN", new BigDecimal("4.56734"));

        when(exchangeRateRepository.findCommonSnapshotForDate(TEST_DATE, "EUR", "PLN"))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findByRateDateAndBaseCurrency(TEST_DATE, null))
                .thenReturn(List.of(toRate));

        // Act & Assert
        assertThatThrownBy(() -> service.calculateExchange("EUR", "PLN", TEST_DATE))
                .isInstanceOf(RateNotFoundException.class)
                .hasMessageContaining("Required currencies not available");

        // Verify no usage increments on failure
        verify(usageRepository, never()).incrementUsageAtomic(any(), any(), any());
    }

    @Test
    void calculateExchange_toCurrencyMissing_throwsException() {
        // Arrange
        ExchangeRateEntity fromRate = createRateEntity("EUR", BigDecimal.ONE);

        when(exchangeRateRepository.findCommonSnapshotForDate(TEST_DATE, "EUR", "PLN"))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findByRateDateAndBaseCurrency(TEST_DATE, null))
                .thenReturn(List.of(fromRate));

        // Act & Assert
        assertThatThrownBy(() -> service.calculateExchange("EUR", "PLN", TEST_DATE))
                .isInstanceOf(RateNotFoundException.class)
                .hasMessageContaining("Required currencies not available");

        // Verify no usage increments on failure
        verify(usageRepository, never()).incrementUsageAtomic(any(), any(), any());
    }

    @Test
    void calculateExchange_noLatestRateAvailable_throwsException() {
        // Arrange
        when(exchangeRateRepository.findLatestCommonSnapshot("EUR", "PLN"))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findLatestRateDate()).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.calculateExchange("EUR", "PLN", null))
                .isInstanceOf(RateNotFoundException.class)
                .hasMessageContaining("No exchange rates available");
    }

    @Test
    void calculateExchange_normalizesToUppercase() {
        // Arrange
        ExchangeRateEntity fromRate = createRateEntity("EUR", BigDecimal.ONE);
        ExchangeRateEntity toRate = createRateEntity("PLN", new BigDecimal("4.56734"));

        when(exchangeRateRepository.findCommonSnapshotForDate(TEST_DATE, "EUR", "PLN"))
                .thenReturn(Optional.of(new Object[]{java.sql.Date.valueOf(TEST_DATE), BASE_CURRENCY}));

        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(TEST_DATE, BASE_CURRENCY, "EUR"))
                .thenReturn(Optional.of(fromRate));
        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(TEST_DATE, BASE_CURRENCY, "PLN"))
                .thenReturn(Optional.of(toRate));

        when(usageRepository.incrementUsageAtomic(any(), any(), any())).thenReturn(1);
        when(usageRepository.getTotalQueryCountForCurrency("EUR")).thenReturn(1L);
        when(usageRepository.getTotalQueryCountForCurrency("PLN")).thenReturn(1L);

        // Act - provide lowercase currency codes
        ExchangeResult result = service.calculateExchange("eur", "pln", TEST_DATE);

        // Assert - result contains uppercase codes
        assertThat(result.from()).isEqualTo("EUR");
        assertThat(result.to()).isEqualTo("PLN");

        // Verify uppercase used in repository calls
        verify(exchangeRateRepository).findCommonSnapshotForDate(TEST_DATE, "EUR", "PLN");
    }

    private ExchangeRateEntity createRateEntity(String currencyCode, BigDecimal rateValue) {
        return new ExchangeRateEntity(TEST_DATE, BASE_CURRENCY, currencyCode, rateValue);
    }
}
