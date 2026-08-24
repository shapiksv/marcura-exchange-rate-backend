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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for snapshot resolution logic in ExchangeApplicationService.
 * <p>
 * Verifies:
 * - Latest complete snapshot fallback (when latest date incomplete)
 * - Deterministic base selection (when multiple bases exist)
 * - Explicit date behavior (no fallback to other dates)
 * - Same-currency behavior (EUR→EUR requires only EUR to exist)
 */
@ExtendWith(MockitoExtension.class)
class ExchangeSnapshotResolutionTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private CurrencyUsageDailyRepository usageRepository;

    private ExchangeApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeApplicationService(exchangeRateRepository, usageRepository);
    }

    /**
     * Scenario: Latest date (2026-08-24) contains only EUR and USD.
     * Previous date (2026-08-23) contains EUR, USD, and PLN.
     * <p>
     * Request: USD → PLN, date omitted
     * <p>
     * Expected: Service must use 2026-08-23 (latest complete snapshot containing both currencies).
     */
    @Test
    void latestCommonSnapshot_fallsBackToOlderDate_whenLatestIncomplete() {
        // Arrange
        LocalDate latestDate = LocalDate.of(2026, 8, 24);
        LocalDate olderCompleteDate = LocalDate.of(2026, 8, 23);

        // Mock: Latest common snapshot query returns older date
        when(exchangeRateRepository.findLatestCommonSnapshot("USD", "PLN"))
                .thenReturn(Optional.of(new Object[]{
                        java.sql.Date.valueOf(olderCompleteDate),
                        "EUR"
                }));

        // Mock: Rates exist for the older date
        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(olderCompleteDate, "EUR", "USD"))
                .thenReturn(Optional.of(createRateEntity(olderCompleteDate, "EUR", "USD", new BigDecimal("1.0836"))));
        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(olderCompleteDate, "EUR", "PLN"))
                .thenReturn(Optional.of(createRateEntity(olderCompleteDate, "EUR", "PLN", new BigDecimal("4.56734"))));

        when(usageRepository.incrementUsageAtomic(any(), any(), any())).thenReturn(1);
        when(usageRepository.getTotalQueryCountForCurrency(any())).thenReturn(1L);

        // Act
        ExchangeResult result = service.calculateExchange("USD", "PLN", null);

        // Assert
        assertThat(result.date()).isEqualTo(olderCompleteDate);
        assertThat(result.from()).isEqualTo("USD");
        assertThat(result.to()).isEqualTo("PLN");

        // Verify the database query used the correct method
        verify(exchangeRateRepository).findLatestCommonSnapshot("USD", "PLN");
        verify(exchangeRateRepository, never()).findLatestRateDate();
    }

    /**
     * Scenario: Date 2026-08-24 has two base currencies:
     * - base=EUR with EUR, USD, GBP
     * - base=USD with USD, EUR, GBP
     * <p>
     * Request: EUR → GBP, date=2026-08-24
     * <p>
     * Expected: Service must use deterministic base selection (alphabetically first: EUR < USD).
     */
    @Test
    void explicitDateSnapshot_selectsDeterministicBase_whenMultipleBasesExist() {
        // Arrange
        LocalDate date = LocalDate.of(2026, 8, 24);

        // Mock: Common snapshot query returns first base alphabetically
        when(exchangeRateRepository.findCommonSnapshotForDate(date, "EUR", "GBP"))
                .thenReturn(Optional.of(new Object[]{
                        java.sql.Date.valueOf(date),
                        "EUR"  // Alphabetically first base
                }));

        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(date, "EUR", "EUR"))
                .thenReturn(Optional.of(createRateEntity(date, "EUR", "EUR", BigDecimal.ONE)));
        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(date, "EUR", "GBP"))
                .thenReturn(Optional.of(createRateEntity(date, "EUR", "GBP", new BigDecimal("0.8573"))));

        when(usageRepository.incrementUsageAtomic(any(), any(), any())).thenReturn(1);
        when(usageRepository.getTotalQueryCountForCurrency(any())).thenReturn(1L);

        // Act
        ExchangeResult result = service.calculateExchange("EUR", "GBP", date);

        // Assert
        assertThat(result.date()).isEqualTo(date);

        // Verify rates were loaded from base=EUR (not base=USD)
        verify(exchangeRateRepository).findByRateDateAndBaseCurrencyAndCurrencyCode(date, "EUR", "EUR");
        verify(exchangeRateRepository).findByRateDateAndBaseCurrencyAndCurrencyCode(date, "EUR", "GBP");
    }

    /**
     * Scenario: Explicit date provided, but requested currencies not available on that date.
     * <p>
     * Request: EUR → PLN, date=2026-08-24
     * But 2026-08-24 contains only EUR and USD (PLN missing).
     * <p>
     * Expected: Service must return 404, NOT fall back to another date.
     */
    @Test
    void explicitDate_returns404_whenCurrenciesMissing_noFallback() {
        // Arrange
        LocalDate requestedDate = LocalDate.of(2026, 8, 24);

        // Mock: No common snapshot for requested date
        when(exchangeRateRepository.findCommonSnapshotForDate(requestedDate, "EUR", "PLN"))
                .thenReturn(Optional.empty());

        // Mock: Some rates exist for the date (but not the requested pair)
        when(exchangeRateRepository.findByRateDateAndBaseCurrency(requestedDate, null))
                .thenReturn(java.util.List.of(
                        createRateEntity(requestedDate, "EUR", "EUR", BigDecimal.ONE),
                        createRateEntity(requestedDate, "EUR", "USD", new BigDecimal("1.0836"))
                ));

        // Act & Assert
        assertThatThrownBy(() -> service.calculateExchange("EUR", "PLN", requestedDate))
                .isInstanceOf(RateNotFoundException.class)
                .hasMessageContaining("Required currencies not available for date " + requestedDate);

        // Verify no fallback to another date
        verify(exchangeRateRepository, never()).findLatestCommonSnapshot(any(), any());
    }

    /**
     * Scenario: Same-currency request (EUR → EUR).
     * <p>
     * Expected: Service requires only EUR to exist in the snapshot.
     * Usage counter incremented twice for EUR.
     */
    @Test
    void sameCurrencyRequest_requiresOnlyOneCurrency() {
        // Arrange
        LocalDate date = LocalDate.of(2026, 8, 24);

        // Mock: Common snapshot with same currency (EUR, EUR) treated as (EUR, EUR)
        when(exchangeRateRepository.findCommonSnapshotForDate(date, "EUR", "EUR"))
                .thenReturn(Optional.of(new Object[]{
                        java.sql.Date.valueOf(date),
                        "EUR"
                }));

        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(date, "EUR", "EUR"))
                .thenReturn(Optional.of(createRateEntity(date, "EUR", "EUR", BigDecimal.ONE)));

        when(usageRepository.incrementUsageAtomic(any(), any(), any())).thenReturn(1);
        when(usageRepository.getTotalQueryCountForCurrency("EUR")).thenReturn(2L);

        // Act
        ExchangeResult result = service.calculateExchange("EUR", "EUR", date);

        // Assert
        assertThat(result.from()).isEqualTo("EUR");
        assertThat(result.to()).isEqualTo("EUR");
        assertThat(result.exchange()).isEqualByComparingTo(BigDecimal.ONE);

        // Verify EUR usage incremented twice
        verify(usageRepository, times(2)).incrementUsageAtomic(eq("EUR"), any(), any());

        // Verify only one distinct currency lookup
        verify(exchangeRateRepository, times(2)) // Called twice (from and to both EUR)
                .findByRateDateAndBaseCurrencyAndCurrencyCode(date, "EUR", "EUR");
    }

    /**
     * Scenario: Latest snapshot query returns empty because requested pair never exists together.
     * <p>
     * Request: EUR → XYZ, date omitted
     * <p>
     * Expected: Service must return 404 with clear message about missing currency pair.
     */
    @Test
    void latestCommonSnapshot_returns404_whenPairNeverExists() {
        // Arrange
        when(exchangeRateRepository.findLatestCommonSnapshot("EUR", "XYZ"))
                .thenReturn(Optional.empty());

        // Mock: Some rates exist in database (but not this pair)
        when(exchangeRateRepository.findLatestRateDate())
                .thenReturn(Optional.of(LocalDate.of(2026, 8, 24)));

        // Act & Assert
        assertThatThrownBy(() -> service.calculateExchange("EUR", "XYZ", null))
                .isInstanceOf(RateNotFoundException.class)
                .hasMessageContaining("No common snapshot found for currencies EUR and XYZ");
    }

    /**
     * Scenario: Multiple base currencies exist for the same date, deterministic ordering test.
     * <p>
     * Request: USD → GBP, date omitted
     * Latest date has base=USD and base=GBP.
     * <p>
     * Expected: Service uses alphabetically first base (GBP < USD).
     */
    @Test
    void latestCommonSnapshot_usesDeterministicBase_whenMultipleBasesAvailable() {
        // Arrange
        LocalDate latestDate = LocalDate.of(2026, 8, 24);

        // Mock: Latest common snapshot returns first base alphabetically
        when(exchangeRateRepository.findLatestCommonSnapshot("USD", "GBP"))
                .thenReturn(Optional.of(new Object[]{
                        java.sql.Date.valueOf(latestDate),
                        "EUR"  // Base selected deterministically
                }));

        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(latestDate, "EUR", "USD"))
                .thenReturn(Optional.of(createRateEntity(latestDate, "EUR", "USD", new BigDecimal("1.0836"))));
        when(exchangeRateRepository.findByRateDateAndBaseCurrencyAndCurrencyCode(latestDate, "EUR", "GBP"))
                .thenReturn(Optional.of(createRateEntity(latestDate, "EUR", "GBP", new BigDecimal("0.8573"))));

        when(usageRepository.incrementUsageAtomic(any(), any(), any())).thenReturn(1);
        when(usageRepository.getTotalQueryCountForCurrency(any())).thenReturn(1L);

        // Act
        ExchangeResult result = service.calculateExchange("USD", "GBP", null);

        // Assert
        assertThat(result.date()).isEqualTo(latestDate);

        // Verify deterministic base was used
        verify(exchangeRateRepository).findByRateDateAndBaseCurrencyAndCurrencyCode(latestDate, "EUR", "USD");
        verify(exchangeRateRepository).findByRateDateAndBaseCurrencyAndCurrencyCode(latestDate, "EUR", "GBP");
    }

    private ExchangeRateEntity createRateEntity(LocalDate rateDate, String baseCurrency, String currencyCode, BigDecimal rateValue) {
        return new ExchangeRateEntity(rateDate, baseCurrency, currencyCode, rateValue);
    }
}
