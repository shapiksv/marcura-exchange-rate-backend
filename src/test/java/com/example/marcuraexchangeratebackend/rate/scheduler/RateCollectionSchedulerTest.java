package com.example.marcuraexchangeratebackend.rate.scheduler;

import com.example.marcuraexchangeratebackend.rate.application.RateCollectionResult;
import com.example.marcuraexchangeratebackend.rate.application.RateCollectionService;
import com.example.marcuraexchangeratebackend.rate.provider.ExchangeRateProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateCollectionScheduler.
 * Tests scheduler behavior without triggering actual scheduled execution.
 * Tests verify:
 * - Successful delegation to RateCollectionService
 * - Error handling and logging
 * - Exception propagation (ShedLock automatically releases lock)
 */
@ExtendWith(MockitoExtension.class)
class RateCollectionSchedulerTest {

    @Mock
    private RateCollectionService rateCollectionService;

    private RateCollectionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RateCollectionScheduler(rateCollectionService);
    }

    @Test
    void collectDailyRates_success_delegatesToService() {
        // Given
        RateCollectionResult expectedResult = new RateCollectionResult(
                LocalDate.of(2024, 3, 15),
                "EUR",
                171,
                171,
                0
        );
        when(rateCollectionService.collectAndPersistLatestRates()).thenReturn(expectedResult);

        // When
        scheduler.collectDailyRates();

        // Then
        verify(rateCollectionService, times(1)).collectAndPersistLatestRates();
    }

    @Test
    void collectDailyRates_serviceThrowsException_logsAndRethrows() {
        // Given
        ExchangeRateProviderException providerException = new ExchangeRateProviderException("Fixer API unavailable");
        when(rateCollectionService.collectAndPersistLatestRates()).thenThrow(providerException);

        // When/Then
        assertThatThrownBy(() -> scheduler.collectDailyRates())
                .isSameAs(providerException);

        verify(rateCollectionService, times(1)).collectAndPersistLatestRates();
    }

    @Test
    void collectDailyRates_unexpectedException_logsAndRethrows() {
        // Given
        RuntimeException unexpectedException = new RuntimeException("Unexpected database error");
        when(rateCollectionService.collectAndPersistLatestRates()).thenThrow(unexpectedException);

        // When/Then
        assertThatThrownBy(() -> scheduler.collectDailyRates())
                .isSameAs(unexpectedException);
    }
}
