package com.example.marcuraexchangeratebackend.rate.application;

import com.example.marcuraexchangeratebackend.rate.domain.RateSnapshot;
import com.example.marcuraexchangeratebackend.rate.provider.ExchangeRateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Application service for collecting and persisting exchange rates.
 * <p>
 * Orchestrates:
 * 1. HTTP fetch from external provider (outside transaction)
 * 2. Response validation and normalization
 * 3. Transactional database persistence via RatePersistenceService
 * <p>
 * Transaction boundary: HTTP fetch executes BEFORE @Transactional persistence.
 * This prevents holding database connections open during external HTTP calls.
 */
@Service
public class RateCollectionService {
    private static final Logger log = LoggerFactory.getLogger(RateCollectionService.class);

    private final ExchangeRateProvider provider;
    private final RatePersistenceService persistenceService;

    public RateCollectionService(ExchangeRateProvider provider, RatePersistenceService persistenceService) {
        this.provider = provider;
        this.persistenceService = persistenceService;
    }

    /**
     * Fetches latest rates from provider and persists them atomically.
     * <p>
     * Flow:
     * 1. HTTP fetch (no transaction)
     * 2. Validation/normalization
     * 3. Transactional upsert (separate service)
     * <p>
     * The snapshot is normalized so base currency appears in rates map with value=1.
     * All currency codes are uppercase.
     * All rates are validated (positive, non-null).
     *
     * @return RateCollectionResult containing statistics
     */
    public RateCollectionResult collectAndPersistLatestRates() {
        log.info("Starting rate collection from external provider");

        // Step 1: HTTP fetch (outside transaction)
        RateSnapshot snapshot = provider.fetchLatestRates();

        // Step 2: Log concise metadata (not full payload)
        log.info("Fetched Fixer snapshot: date={}, base={}, currencies={}",
                snapshot.rateDate(), snapshot.baseCurrency(), snapshot.rates().size());

        // Step 3: Transactional persistence (separate service defines @Transactional boundary)
        RatePersistenceService.RatePersistenceResult persistenceResult = persistenceService.upsertRates(
                snapshot.rateDate(),
                snapshot.baseCurrency(),
                snapshot.rates()
        );

        log.info("Rate collection complete: date={}, base={}, inserted={}, updated={}",
                snapshot.rateDate(), snapshot.baseCurrency(),
                persistenceResult.inserted(), persistenceResult.updated());

        return new RateCollectionResult(
                snapshot.rateDate(),
                snapshot.baseCurrency(),
                snapshot.rates().size(),
                persistenceResult.inserted(),
                persistenceResult.updated()
        );
    }
}
