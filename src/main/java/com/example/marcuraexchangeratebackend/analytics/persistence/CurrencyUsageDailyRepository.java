package com.example.marcuraexchangeratebackend.analytics.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for CurrencyUsageDailyEntity.
 *
 * Provides atomic upsert operations for concurrent usage tracking.
 */
@Repository
public interface CurrencyUsageDailyRepository extends JpaRepository<CurrencyUsageDailyEntity, Long> {

    /**
     * Find usage record for a specific currency on a specific date.
     */
    Optional<CurrencyUsageDailyEntity> findByCurrencyCodeAndQueryDate(String currencyCode, LocalDate queryDate);

    /**
     * Atomically increment usage count for a currency on a specific date.
     * Uses PostgreSQL ON CONFLICT clause for concurrency safety.
     *
     * @param currencyCode    the currency code
     * @param queryDate       the query date
     * @param lastQueriedAt   the timestamp of this query
     * @return number of rows affected (always 1)
     */
    @Modifying
    @Query(value = """
            INSERT INTO currency_usage_daily (currency_code, query_date, query_count, last_queried_at)
            VALUES (:currencyCode, :queryDate, 1, :lastQueriedAt)
            ON CONFLICT (currency_code, query_date)
            DO UPDATE SET
                query_count = currency_usage_daily.query_count + 1,
                last_queried_at = EXCLUDED.last_queried_at
            """, nativeQuery = true)
    int incrementUsageAtomic(
            @Param("currencyCode") String currencyCode,
            @Param("queryDate") LocalDate queryDate,
            @Param("lastQueriedAt") OffsetDateTime lastQueriedAt
    );

    /**
     * Get total query count for a currency across all dates.
     */
    @Query("SELECT COALESCE(SUM(u.queryCount), 0) FROM CurrencyUsageDailyEntity u WHERE u.currencyCode = :currencyCode")
    Long getTotalQueryCountForCurrency(@Param("currencyCode") String currencyCode);

    /**
     * Get all usage records for a specific currency.
     */
    List<CurrencyUsageDailyEntity> findByCurrencyCodeOrderByQueryDateDesc(String currencyCode);

    /**
     * Get all usage records ordered by total query count (for analytics).
     */
    @Query("SELECT u FROM CurrencyUsageDailyEntity u ORDER BY u.queryCount DESC")
    List<CurrencyUsageDailyEntity> findAllOrderByQueryCountDesc();
}
