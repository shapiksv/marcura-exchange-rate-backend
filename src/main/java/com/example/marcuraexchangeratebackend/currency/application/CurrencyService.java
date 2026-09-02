package com.example.marcuraexchangeratebackend.currency.application;

import com.example.marcuraexchangeratebackend.common.error.RateNotFoundException;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for currency-related operations.
 * <p>
 * Provides read-only access to available currencies from the latest exchange rate snapshot.
 */
@Service
public class CurrencyService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);

    private final ExchangeRateRepository exchangeRateRepository;

    public CurrencyService(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /**
     * Get list of available currencies from the most recent exchange rate snapshot.
     * <p>
     * Returns all unique currency codes from the latest available rate date.
     * <p>
     * The list includes:
     * - All currencies from the latest snapshot
     * - The base currency (if available)
     * - Sorted alphabetically for consistent ordering
     *
     * @return sorted list of unique currency codes
     * @throws RateNotFoundException if no exchange rates exist in the database
     */
    @Transactional(readOnly = true)
    public List<String> getAvailableCurrencies() {
        log.debug("Fetching available currencies from latest snapshot");

        // Get all distinct currencies from the latest snapshot in a single query
        List<String> currencies = exchangeRateRepository.findDistinctCurrenciesFromLatestSnapshot();

        if (currencies.isEmpty()) {
            log.warn("No exchange rates found in database");
            throw new RateNotFoundException("No exchange rates available");
        }

        log.info("Found {} currencies in latest snapshot", currencies.size());

        return currencies;
    }
}
