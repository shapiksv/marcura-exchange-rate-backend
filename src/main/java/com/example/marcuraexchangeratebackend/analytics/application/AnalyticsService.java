package com.example.marcuraexchangeratebackend.analytics.application;

import com.example.marcuraexchangeratebackend.analytics.api.CurrencyUsageSummary;
import com.example.marcuraexchangeratebackend.analytics.api.DailyUsageEntry;
import com.example.marcuraexchangeratebackend.analytics.persistence.CurrencyUsageDailyEntity;
import com.example.marcuraexchangeratebackend.analytics.persistence.CurrencyUsageDailyRepository;
import com.example.marcuraexchangeratebackend.analytics.persistence.CurrencyUsageSummaryProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Application service for currency usage analytics.
 * <p>
 * Provides read-only access to usage statistics without modifying counters.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final CurrencyUsageDailyRepository usageRepository;

    public AnalyticsService(CurrencyUsageDailyRepository usageRepository) {
        this.usageRepository = usageRepository;
    }

    /**
     * Get top currencies ordered by total query count.
     * <p>
     * Aggregates counts across all dates for each currency.
     * Results are ordered by total count descending, then currency code ascending (deterministic).
     * <p>
     * Does NOT increment usage counters.
     *
     * @return list of currency usage summaries
     */
    @Transactional(readOnly = true)
    public List<CurrencyUsageSummary> getTopCurrencies() {
        log.debug("Fetching top currencies by usage");

        List<CurrencyUsageSummaryProjection> results = usageRepository.findUsageSummaryGroupedByCurrency();

        return results.stream()
                .map(projection -> new CurrencyUsageSummary(
                        projection.getCurrencyCode(),
                        projection.getTotalCount(),
                        parseTimestamp(projection.getLastQueried())
                ))
                .toList();
    }

    /**
     * Get daily usage breakdown ordered by date and currency.
     * <p>
     * Returns all usage records for analytics dashboard.
     * Results are ordered by query date ascending, then currency code ascending (deterministic).
     * <p>
     * Does NOT increment usage counters.
     *
     * @return list of daily usage entries
     */
    @Transactional(readOnly = true)
    public List<DailyUsageEntry> getDailyUsage() {
        log.debug("Fetching daily usage records");

        List<CurrencyUsageDailyEntity> entities = usageRepository.findAllOrderByDateAndCurrency();

        return entities.stream()
                .map(entity -> new DailyUsageEntry(
                        entity.getQueryDate(),
                        entity.getCurrencyCode(),
                        entity.getQueryCount()
                ))
                .toList();
    }

    /**
     * Parse ISO 8601 timestamp string from PostgreSQL to OffsetDateTime.
     * <p>
     * PostgreSQL TIMESTAMP WITH TIME ZONE values are returned as ISO 8601 strings
     * when using CAST to VARCHAR in native queries.
     */
    private OffsetDateTime parseTimestamp(String timestamp) {
        return OffsetDateTime.parse(timestamp);
    }
}
