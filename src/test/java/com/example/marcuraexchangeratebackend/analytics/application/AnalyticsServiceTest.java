package com.example.marcuraexchangeratebackend.analytics.application;

import com.example.marcuraexchangeratebackend.analytics.api.CurrencyUsageSummary;
import com.example.marcuraexchangeratebackend.analytics.api.DailyUsageEntry;
import com.example.marcuraexchangeratebackend.analytics.persistence.CurrencyUsageDailyEntity;
import com.example.marcuraexchangeratebackend.analytics.persistence.CurrencyUsageDailyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AnalyticsService.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private CurrencyUsageDailyRepository usageRepository;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(usageRepository);
    }

    @Test
    @DisplayName("Should return top currencies ordered by total count descending")
    void shouldReturnTopCurrenciesOrderedByCount() {
        // Given
        OffsetDateTime now = OffsetDateTime.now();

        Object[] eurData = {"EUR", 142L, now.minusDays(1)};
        Object[] usdData = {"USD", 98L, now.minusDays(2)};
        Object[] plnData = {"PLN", 37L, now.minusDays(3)};

        when(usageRepository.findUsageSummaryGroupedByCurrency())
                .thenReturn(Arrays.asList(eurData, usdData, plnData));

        // When
        List<CurrencyUsageSummary> result = service.getTopCurrencies();

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).currency()).isEqualTo("EUR");
        assertThat(result.get(0).totalCount()).isEqualTo(142L);
        assertThat(result.get(1).currency()).isEqualTo("USD");
        assertThat(result.get(1).totalCount()).isEqualTo(98L);
        assertThat(result.get(2).currency()).isEqualTo("PLN");
        assertThat(result.get(2).totalCount()).isEqualTo(37L);

        verify(usageRepository).findUsageSummaryGroupedByCurrency();
    }

    @Test
    @DisplayName("Should return empty list when no usage data exists")
    void shouldReturnEmptyListWhenNoUsageDataExists() {
        // Given
        when(usageRepository.findUsageSummaryGroupedByCurrency())
                .thenReturn(new ArrayList<>());

        // When
        List<CurrencyUsageSummary> result = service.getTopCurrencies();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle BigInteger total count from PostgreSQL SUM")
    void shouldHandleBigIntegerTotalCount() {
        // Given
        OffsetDateTime now = OffsetDateTime.now();

        // PostgreSQL may return BigInteger for SUM() aggregates
        Object[] eurData = {"EUR", BigInteger.valueOf(999999999L), now};
        
        List<Object[]> resultList = new ArrayList<>();
        resultList.add(eurData);

        when(usageRepository.findUsageSummaryGroupedByCurrency())
                .thenReturn(resultList);

        // When
        List<CurrencyUsageSummary> result = service.getTopCurrencies();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalCount()).isEqualTo(999999999L);
    }

    @Test
    @DisplayName("Should return daily usage ordered by date and currency")
    void shouldReturnDailyUsageOrderedByDateAndCurrency() {
        // Given
        LocalDate date1 = LocalDate.of(2024, 3, 15);
        LocalDate date2 = LocalDate.of(2024, 3, 16);

        CurrencyUsageDailyEntity eur1 = createUsageEntity("EUR", date1, 10L);
        CurrencyUsageDailyEntity usd1 = createUsageEntity("USD", date1, 5L);
        CurrencyUsageDailyEntity eur2 = createUsageEntity("EUR", date2, 12L);

        when(usageRepository.findAllOrderByDateAndCurrency())
                .thenReturn(Arrays.asList(eur1, usd1, eur2));

        // When
        List<DailyUsageEntry> result = service.getDailyUsage();

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).date()).isEqualTo(date1);
        assertThat(result.get(0).currency()).isEqualTo("EUR");
        assertThat(result.get(0).count()).isEqualTo(10L);
        assertThat(result.get(1).date()).isEqualTo(date1);
        assertThat(result.get(1).currency()).isEqualTo("USD");
        assertThat(result.get(2).date()).isEqualTo(date2);
        assertThat(result.get(2).currency()).isEqualTo("EUR");

        verify(usageRepository).findAllOrderByDateAndCurrency();
    }

    @Test
    @DisplayName("Should return empty daily usage when no data exists")
    void shouldReturnEmptyDailyUsageWhenNoDataExists() {
        // Given
        when(usageRepository.findAllOrderByDateAndCurrency())
                .thenReturn(new ArrayList<>());

        // When
        List<DailyUsageEntry> result = service.getDailyUsage();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should include last queried timestamp in top currencies")
    void shouldIncludeLastQueriedTimestampInTopCurrencies() {
        // Given
        OffsetDateTime eurLastQueried = OffsetDateTime.now().minusDays(1);
        OffsetDateTime usdLastQueried = OffsetDateTime.now().minusDays(2);

        Object[] eurData = {"EUR", 100L, eurLastQueried};
        Object[] usdData = {"USD", 50L, usdLastQueried};

        when(usageRepository.findUsageSummaryGroupedByCurrency())
                .thenReturn(Arrays.asList(eurData, usdData));

        // When
        List<CurrencyUsageSummary> result = service.getTopCurrencies();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).lastQueried()).isEqualTo(eurLastQueried);
        assertThat(result.get(1).lastQueried()).isEqualTo(usdLastQueried);
    }

    private CurrencyUsageDailyEntity createUsageEntity(
            String currencyCode,
            LocalDate queryDate,
            Long queryCount
    ) {
        CurrencyUsageDailyEntity entity = new CurrencyUsageDailyEntity(
                currencyCode,
                queryDate,
                OffsetDateTime.now()
        );
        entity.setQueryCount(queryCount);
        return entity;
    }
}
