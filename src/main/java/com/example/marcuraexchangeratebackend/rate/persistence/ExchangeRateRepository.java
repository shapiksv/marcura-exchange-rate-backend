package com.example.marcuraexchangeratebackend.rate.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ExchangeRateEntity.
 *
 * Provides methods to query exchange rates by date, currency, and base currency.
 * Includes atomic PostgreSQL upsert for concurrent-safe rate ingestion.
 */
@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRateEntity, Long> {

    /**
     * Find exchange rate for a specific currency on a specific date with a specific base currency.
     */
    Optional<ExchangeRateEntity> findByRateDateAndBaseCurrencyAndCurrencyCode(
            LocalDate rateDate,
            String baseCurrency,
            String currencyCode
    );

    /**
     * Find all exchange rates for a specific date and base currency.
     */
    List<ExchangeRateEntity> findByRateDateAndBaseCurrency(LocalDate rateDate, String baseCurrency);

    /**
     * Find all exchange rates for a specific currency within a date range.
     */
    List<ExchangeRateEntity> findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
            String currencyCode,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Get the most recent rate date available in the database.
     */
    @Query("SELECT MAX(e.rateDate) FROM ExchangeRateEntity e")
    Optional<LocalDate> findLatestRateDate();

    /**
     * Get the most recent rate date for a specific base currency.
     */
    @Query("SELECT MAX(e.rateDate) FROM ExchangeRateEntity e WHERE e.baseCurrency = :baseCurrency")
    Optional<LocalDate> findLatestRateDateForBase(@Param("baseCurrency") String baseCurrency);

    /**
     * Atomic PostgreSQL upsert using INSERT ... ON CONFLICT.
     * <p>
     * This method is concurrency-safe:
     * - No duplicate rows even under concurrent executions
     * - No unique constraint violations
     * - Existing rate values can be updated
     * <p>
     * Note: JPA lifecycle callbacks (@PrePersist, @PreUpdate) do not execute.
     * Timestamps are managed explicitly in the SQL.
     *
     * @param rateDate     the date of the rate
     * @param baseCurrency the base currency
     * @param currencyCode the target currency
     * @param rateValue    the exchange rate value
     * @param now          current timestamp for created_at/updated_at
     * @return number of rows affected (1 for insert or update)
     */
    @Modifying
    @Query(value = """
            INSERT INTO exchange_rate (rate_date, base_currency, currency_code, rate_value, created_at, updated_at)
            VALUES (:rateDate, :baseCurrency, :currencyCode, :rateValue, :now, :now)
            ON CONFLICT (rate_date, base_currency, currency_code)
            DO UPDATE SET
                rate_value = EXCLUDED.rate_value,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertRate(
            @Param("rateDate") LocalDate rateDate,
            @Param("baseCurrency") String baseCurrency,
            @Param("currencyCode") String currencyCode,
            @Param("rateValue") BigDecimal rateValue,
            @Param("now") OffsetDateTime now
    );

    /**
     * Find the ID of an exchange rate by natural key.
     * Used to determine if a row existed before upsert (to differentiate insert from update).
     */
    @Query("SELECT e.id FROM ExchangeRateEntity e WHERE e.rateDate = :rateDate AND e.baseCurrency = :baseCurrency AND e.currencyCode = :currencyCode")
    Long findIdByNaturalKey(
            @Param("rateDate") LocalDate rateDate,
            @Param("baseCurrency") String baseCurrency,
            @Param("currencyCode") String currencyCode
    );

    /**
     * Find a common snapshot (rate_date, base_currency) that contains both specified currencies
     * for a specific date.
     * <p>
     * Returns the snapshot with deterministic ordering: rate_date DESC, base_currency ASC.
     * <p>
     * This ensures that if multiple base snapshots exist for the same date, the selection is deterministic.
     *
     * @param rateDate the specific date to search
     * @param fromCurrency first required currency
     * @param toCurrency second required currency
     * @return Optional containing projection with rate_date and base_currency, or empty if no common snapshot exists
     */
    @Query(value = """
            SELECT e1.rate_date AS rateDate, e1.base_currency AS baseCurrency
            FROM exchange_rate e1
            WHERE e1.rate_date = :rateDate
              AND e1.currency_code = :fromCurrency
              AND EXISTS (
                  SELECT 1 FROM exchange_rate e2
                  WHERE e2.rate_date = e1.rate_date
                    AND e2.base_currency = e1.base_currency
                    AND e2.currency_code = :toCurrency
              )
            ORDER BY e1.base_currency ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<CommonSnapshotProjection> findCommonSnapshotForDate(
            @Param("rateDate") LocalDate rateDate,
            @Param("fromCurrency") String fromCurrency,
            @Param("toCurrency") String toCurrency
    );

    /**
     * Find the latest common snapshot (rate_date, base_currency) that contains both specified currencies.
     * <p>
     * Returns the snapshot with deterministic ordering: rate_date DESC, base_currency ASC.
     * <p>
     * This means:
     * - Use the most recent date that has both currencies
     * - If multiple base currencies exist for that date, use the alphabetically first base
     *
     * @param fromCurrency first required currency
     * @param toCurrency second required currency
     * @return Optional containing projection with rate_date and base_currency, or empty if no common snapshot exists
     */
    @Query(value = """
            SELECT e1.rate_date AS rateDate, e1.base_currency AS baseCurrency
            FROM exchange_rate e1
            WHERE e1.currency_code = :fromCurrency
              AND EXISTS (
                  SELECT 1 FROM exchange_rate e2
                  WHERE e2.rate_date = e1.rate_date
                    AND e2.base_currency = e1.base_currency
                    AND e2.currency_code = :toCurrency
              )
            ORDER BY e1.rate_date DESC, e1.base_currency ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<CommonSnapshotProjection> findLatestCommonSnapshot(
            @Param("fromCurrency") String fromCurrency,
            @Param("toCurrency") String toCurrency
    );

    /**
     * Find all historical rate data for a currency pair within a date range in a SINGLE query.
     * <p>
     * Eliminates N+1 query pattern by fetching all needed data in one database call.
     * <p>
     * For each available date in the range:
     * 1. Selects the alphabetically first base_currency that contains both requested currencies
     * 2. Fetches both currency rates from that snapshot
     * <p>
     * Returns results ordered by rate_date ASC.
     * <p>
     * Query uses DISTINCT ON to ensure one snapshot per date, with deterministic base selection.
     *
     * @param fromCurrency first required currency
     * @param toCurrency second required currency
     * @param startDate inclusive start of date range
     * @param endDate inclusive end of date range
     * @return List of HistoricalRateProjection containing [rate_date, base_currency, from_rate, to_rate]
     */
    @Query(value = """
            WITH selected_snapshots AS (
                SELECT DISTINCT ON (f.rate_date)
                       f.rate_date,
                       f.base_currency
                FROM exchange_rate f
                WHERE f.currency_code = :fromCurrency
                  AND f.rate_date BETWEEN :fromDate AND :toDate
                  AND EXISTS (
                      SELECT 1 FROM exchange_rate t
                      WHERE t.rate_date = f.rate_date
                        AND t.base_currency = f.base_currency
                        AND t.currency_code = :toCurrency
                  )
                ORDER BY f.rate_date ASC,
                         f.base_currency ASC
            )
            SELECT
                s.rate_date AS rateDate,
                s.base_currency AS baseCurrency,
                fr.rate_value AS fromRate,
                tr.rate_value AS toRate
            FROM selected_snapshots s
            JOIN exchange_rate fr
              ON fr.rate_date = s.rate_date
             AND fr.base_currency = s.base_currency
             AND fr.currency_code = :fromCurrency
            JOIN exchange_rate tr
              ON tr.rate_date = s.rate_date
             AND tr.base_currency = s.base_currency
             AND tr.currency_code = :toCurrency
            ORDER BY s.rate_date ASC
            """, nativeQuery = true)
    List<HistoricalRateProjection> findHistoricalRatesInSingleQuery(
            @Param("fromCurrency") String fromCurrency,
            @Param("toCurrency") String toCurrency,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
