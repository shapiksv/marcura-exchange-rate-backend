package com.example.marcuraexchangeratebackend.rate.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ExchangeRateEntity.
 *
 * Provides methods to query exchange rates by date, currency, and base currency.
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
}
