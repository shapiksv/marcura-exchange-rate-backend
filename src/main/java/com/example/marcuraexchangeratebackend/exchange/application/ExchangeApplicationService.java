package com.example.marcuraexchangeratebackend.exchange.application;

import com.example.marcuraexchangeratebackend.analytics.persistence.CurrencyUsageDailyRepository;
import com.example.marcuraexchangeratebackend.common.error.RateNotFoundException;
import com.example.marcuraexchangeratebackend.exchange.domain.ExchangeRateCalculator;
import com.example.marcuraexchangeratebackend.rate.persistence.CommonSnapshotProjection;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateEntity;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for exchange rate calculations.
 *
 * Handles:
 * - Loading rates from the database
 * - Calculating spread-adjusted exchange rates
 * - Atomically incrementing usage counters for both currencies
 * - Returning current usage counts
 *
 * Transaction boundary: This service is @Transactional to ensure that the rate lookup,
 * calculation, and usage increment happen atomically.
 */
@Service
public class ExchangeApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeApplicationService.class);

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyUsageDailyRepository usageRepository;
    private final ExchangeRateCalculator calculator;

    public ExchangeApplicationService(
            ExchangeRateRepository exchangeRateRepository,
            CurrencyUsageDailyRepository usageRepository
    ) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.usageRepository = usageRepository;
        this.calculator = new ExchangeRateCalculator();
    }

    /**
     * Calculate the exchange rate between two currencies.
     * <p>
     * If date is null, uses the latest available rate date.
     * Both currencies must be available for the same rate date and base currency.
     * <p>
     * On success, increments usage counters for both currencies atomically.
     *
     * @param fromCurrency the source currency code (normalized to uppercase)
     * @param toCurrency   the target currency code (normalized to uppercase)
     * @param date         the rate date (null for latest available)
     * @return the exchange calculation result
     * @throws RateNotFoundException if required rates are not available
     */
    @Transactional
    public ExchangeResult calculateExchange(String fromCurrency, String toCurrency, LocalDate date) {
        Objects.requireNonNull(fromCurrency, "fromCurrency must not be null");
        Objects.requireNonNull(toCurrency, "toCurrency must not be null");

        // Normalize to uppercase
        String from = fromCurrency.toUpperCase(java.util.Locale.ROOT);
        String to = toCurrency.toUpperCase(java.util.Locale.ROOT);

        // Resolve the rate date and base currency
        RateSnapshot snapshot = resolveRateSnapshot(from, to, date);

        log.debug("Calculating exchange: from={}, to={}, date={}, base={}",
                from, to, snapshot.rateDate(), snapshot.baseCurrency());

        // Load both rates from the same snapshot
        ExchangeRateEntity fromRate = exchangeRateRepository
                .findByRateDateAndBaseCurrencyAndCurrencyCode(
                        snapshot.rateDate(), snapshot.baseCurrency(), from)
                .orElseThrow(() -> new RateNotFoundException(
                        "Rate not found for currency " + from + " on date " + snapshot.rateDate()
                ));

        ExchangeRateEntity toRate = exchangeRateRepository
                .findByRateDateAndBaseCurrencyAndCurrencyCode(
                        snapshot.rateDate(), snapshot.baseCurrency(), to)
                .orElseThrow(() -> new RateNotFoundException(
                        "Rate not found for currency " + to + " on date " + snapshot.rateDate()
                ));

        // Calculate the spread-adjusted exchange rate
        BigDecimal adjustedRate = calculator.calculateAdjustedRate(
                fromRate.getRateValue(),
                toRate.getRateValue(),
                snapshot.baseCurrency(),
                from,
                to
        );

        // Atomically increment usage for both currencies
        LocalDate queryDate = LocalDate.now();
        OffsetDateTime queryTime = OffsetDateTime.now();

        incrementUsageAtomic(from, queryDate, queryTime);
        // For same-currency case (EUR/EUR), increment twice as per requirement
        incrementUsageAtomic(to, queryDate, queryTime);

        // Retrieve updated counts
        Long fromCount = usageRepository.getTotalQueryCountForCurrency(from);
        Long toCount = usageRepository.getTotalQueryCountForCurrency(to);

        return new ExchangeResult(
                from,
                to,
                adjustedRate,
                snapshot.rateDate(),
                fromCount,
                toCount
        );
    }

    /**
     * Resolve the rate snapshot to use for the calculation.
     * <p>
     * If date is provided, finds a common snapshot for that specific date.
     * If date is null, finds the latest common snapshot.
     * <p>
     * A common snapshot is one that contains both requested currencies
     * for the same (rate_date, base_currency) combination.
     * <p>
     * Deterministic ordering: rate_date DESC, base_currency ASC.
     * This means if multiple base currencies exist for the same date,
     * the alphabetically first base currency is selected.
     * <p>
     * Special case: if from == to, only one distinct currency needs to exist.
     *
     * @param from the source currency
     * @param to   the target currency
     * @param date the requested date (null for latest)
     * @return the resolved snapshot (rateDate, baseCurrency)
     * @throws RateNotFoundException if no suitable snapshot exists
     */
    private RateSnapshot resolveRateSnapshot(String from, String to, LocalDate date) {
        // For same-currency requests, only need one currency in the snapshot
        String fromCurrency = from;
        String toCurrency = from.equals(to) ? from : to;

        if (date != null) {
            // Explicit date provided: find common snapshot for this date
            return findSnapshotForDate(fromCurrency, toCurrency, date);
        } else {
            // Find latest common snapshot
            return findLatestSnapshot(fromCurrency, toCurrency);
        }
    }

    /**
     * Find a common snapshot for a specific date that contains both currencies.
     * <p>
     * Uses database query to find a snapshot (rate_date, base_currency) where both currencies exist.
     * If multiple base currencies exist for this date, selects alphabetically first (deterministic).
     *
     * @throws RateNotFoundException if no suitable snapshot exists for the date
     */
    private RateSnapshot findSnapshotForDate(String from, String to, LocalDate date) {
        Optional<CommonSnapshotProjection> snapshotData = exchangeRateRepository
                .findCommonSnapshotForDate(date, from, to);

        if (snapshotData.isEmpty()) {
            // Check if any rates exist for this date at all
            List<ExchangeRateEntity> ratesForDate = exchangeRateRepository
                    .findByRateDateAndBaseCurrency(date, null);

            if (ratesForDate.isEmpty()) {
                throw new RateNotFoundException("No rates available for date " + date);
            } else {
                throw new RateNotFoundException(
                        "Required currencies not available for date " + date +
                                " (missing one or both: " + from + ", " + to + ")"
                );
            }
        }

        CommonSnapshotProjection snapshot = snapshotData.get();
        LocalDate rateDate = snapshot.getRateDate();
        String baseCurrency = snapshot.getBaseCurrency();

        log.debug("Resolved snapshot for explicit date: date={}, base={}", rateDate, baseCurrency);
        return new RateSnapshot(rateDate, baseCurrency);
    }

    /**
     * Find the latest common snapshot that contains both currencies.
     * <p>
     * Uses database query to find the most recent snapshot where both currencies exist.
     * Searches backwards through dates until a valid snapshot is found.
     * <p>
     * Deterministic ordering: rate_date DESC, base_currency ASC.
     *
     * @throws RateNotFoundException if no suitable snapshot exists
     */
    private RateSnapshot findLatestSnapshot(String from, String to) {
        Optional<CommonSnapshotProjection> snapshotData = exchangeRateRepository
                .findLatestCommonSnapshot(from, to);

        if (snapshotData.isEmpty()) {
            // Check if any rates exist at all
            Optional<LocalDate> anyRateDate = exchangeRateRepository.findLatestRateDate();

            if (anyRateDate.isEmpty()) {
                throw new RateNotFoundException("No exchange rates available");
            } else {
                throw new RateNotFoundException(
                        "No common snapshot found for currencies " + from + " and " + to +
                                " (rates exist but not for this currency pair)"
                );
            }
        }

        CommonSnapshotProjection snapshot = snapshotData.get();
        LocalDate rateDate = snapshot.getRateDate();
        String baseCurrency = snapshot.getBaseCurrency();

        log.debug("Resolved latest common snapshot: date={}, base={}", rateDate, baseCurrency);
        return new RateSnapshot(rateDate, baseCurrency);
    }

    /**
     * Atomically increment usage counter for a currency on a specific date.
     */
    private void incrementUsageAtomic(String currencyCode, LocalDate queryDate, OffsetDateTime queryTime) {
        usageRepository.incrementUsageAtomic(currencyCode, queryDate, queryTime);
    }

    /**
     * Internal record for rate snapshot resolution.
     *
     * @param rateDate     the date of the snapshot
     * @param baseCurrency the base currency of the snapshot
     */
    private record RateSnapshot(LocalDate rateDate, String baseCurrency) {
    }
}
