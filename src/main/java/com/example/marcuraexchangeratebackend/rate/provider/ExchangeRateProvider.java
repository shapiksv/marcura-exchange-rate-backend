package com.example.marcuraexchangeratebackend.rate.provider;

import com.example.marcuraexchangeratebackend.rate.domain.RateSnapshot;

/**
 * Abstraction for fetching exchange rates from external providers.
 * Keeps HTTP transport details separated from domain/application logic.
 */
public interface ExchangeRateProvider {
    /**
     * Fetches the latest exchange rates from the provider.
     *
     * @return immutable RateSnapshot containing rate date, base currency, and rates
     * @throws FixerApiException if provider returns error response
     * @throws ExchangeRateProviderException for transport errors or invalid responses
     */
    RateSnapshot fetchLatestRates();
}
