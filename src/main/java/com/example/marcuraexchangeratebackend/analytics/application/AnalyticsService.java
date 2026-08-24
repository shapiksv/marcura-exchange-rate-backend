package com.example.marcuraexchangeratebackend.analytics.application;

import com.example.marcuraexchangeratebackend.analytics.api.CurrencyUsageSummary;
import com.example.marcuraexchangeratebackend.analytics.api.DailyUsageEntry;
import com.example.marcuraexchangeratebackend.analytics.persistence.CurrencyUsageDailyEntity;
import com.example.marcuraexchangeratebackend.analytics.persistence.CurrencyUsageDailyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
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

        List<Object[]> results = usageRepository.findUsageSummaryGroupedByCurrency();

        return results.stream()
                .map(row -> new CurrencyUsageSummary(
                        (String) row[0],                        // currency_code
                        convertToLong(row[1]),                  // total_count (may be BigInteger or Long)
                        (OffsetDateTime) row[2]                 // last_queried
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
     * Convert numeric aggregate result to Long.
     * PostgreSQL SUM() may return BigInteger for large values.
     */
    private Long convertToLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        } else if (value instanceof BigInteger bigIntValue) {
            return bigIntValue.longValue();
        } else if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        return 0L;
    }
}
