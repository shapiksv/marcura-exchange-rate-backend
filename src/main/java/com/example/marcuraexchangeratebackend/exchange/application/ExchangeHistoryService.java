package com.example.marcuraexchangeratebackend.exchange.application;

import com.example.marcuraexchangeratebackend.common.error.InvalidRequestException;
import com.example.marcuraexchangeratebackend.exchange.api.HistoricalRateEntry;
import com.example.marcuraexchangeratebackend.exchange.domain.ExchangeRateCalculator;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateRepository;
import com.example.marcuraexchangeratebackend.rate.persistence.HistoricalRateProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Application service for historical exchange rate queries.
 * <p>
 * Provides read-only access to historical rate data without modifying usage counters.
 */
@Service
public class ExchangeHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeHistoryService.class);

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateCalculator calculator;

    public ExchangeHistoryService(
            ExchangeRateRepository exchangeRateRepository,
            ExchangeRateCalculator calculator
    ) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.calculator = calculator;
    }

    /**
     * Get historical exchange rates for a currency pair within a date range.
     * <p>
     * For each available date in the range:
     * 1. Finds a common snapshot containing both currencies
     * 2. Calculates both raw and spread-adjusted rates
     * 3. Returns results ordered by date ascending
     * <p>
     * Uses a SINGLE optimized database query to fetch all needed data.
     * <p>
     * Missing dates are omitted (not fabricated).
     * <p>
     * Does NOT increment usage counters.
     *
     * @param from      source currency code
     * @param to        target currency code
     * @param fromDate  start of date range (inclusive)
     * @param toDate    end of date range (inclusive)
     * @return list of historical rate entries, one per available date
     * @throws InvalidRequestException if date range is invalid (fromDate > toDate)
     */
    @Transactional(readOnly = true)
    public List<HistoricalRateEntry> getHistoricalRates(
            String from,
            String to,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        // Normalize currency codes
        String fromCurrency = from.toUpperCase(Locale.ROOT);
        String toCurrency = to.toUpperCase(Locale.ROOT);

        // Validate date range
        if (fromDate.isAfter(toDate)) {
            throw new InvalidRequestException(
                    String.format("Invalid date range: fromDate (%s) must not be after toDate (%s)",
                            fromDate, toDate)
            );
        }

        log.debug("Fetching historical rates for {} -> {} from {} to {}",
                fromCurrency, toCurrency, fromDate, toDate);

        // Handle same-currency case
        if (fromCurrency.equals(toCurrency)) {
            return getHistoricalRatesForSameCurrency(fromCurrency, fromDate, toDate);
        }

        // Fetch all historical rate data in a SINGLE database query
        List<HistoricalRateProjection> projections = exchangeRateRepository.findHistoricalRatesInSingleQuery(
                fromCurrency,
                toCurrency,
                fromDate,
                toDate
        );

        log.debug("Fetched {} historical rate projections for {} -> {} in range {} to {}",
                projections.size(), fromCurrency, toCurrency, fromDate, toDate);

        // Calculate rates for each projection
        return projections.stream()
                .map(projection -> calculateRatesFromProjection(
                        projection,
                        fromCurrency,
                        toCurrency
                ))
                .toList();
    }

    /**
     * Handle same-currency historical query (e.g., EUR -> EUR).
     * Returns rate = 1 for each date where the currency exists.
     * <p>
     * For same-currency, we can reuse the optimized single-query method
     * by passing the same currency for both from and to.
     */
    private List<HistoricalRateEntry> getHistoricalRatesForSameCurrency(
            String currency,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        // Use the optimized query with same currency for both from/to
        List<HistoricalRateProjection> projections = exchangeRateRepository.findHistoricalRatesInSingleQuery(
                currency,
                currency,
                fromDate,
                toDate
        );

        return projections.stream()
                .map(projection -> new HistoricalRateEntry(
                        projection.rateDate(),
                        BigDecimal.ONE,
                        BigDecimal.ONE
                ))
                .toList();
    }

    /**
     * Calculate both raw and adjusted rates from a projection containing all needed data.
     * <p>
     * No additional database queries needed - all data is in the projection.
     */
    private HistoricalRateEntry calculateRatesFromProjection(
            HistoricalRateProjection projection,
            String fromCurrency,
            String toCurrency
    ) {
        // Calculate raw cross rate
        BigDecimal rawCrossRate = calculator.calculateRawCrossRate(
                projection.fromRate(),
                projection.toRate()
        );

        // Calculate spread-adjusted rate
        BigDecimal adjustedRate = calculator.calculateAdjustedRate(
                projection.fromRate(),
                projection.toRate(),
                projection.baseCurrency(),
                fromCurrency,
                toCurrency
        );

        return new HistoricalRateEntry(
                projection.rateDate(),
                rawCrossRate,
                adjustedRate
        );
    }
}
