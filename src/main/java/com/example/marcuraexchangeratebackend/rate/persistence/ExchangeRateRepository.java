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
}
