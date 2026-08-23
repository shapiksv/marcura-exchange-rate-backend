package com.example.marcuraexchangeratebackend.rate.application;

import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Transactional persistence service for exchange rates.
 * Handles atomic database upserts using PostgreSQL ON CONFLICT.
 * <p>
 * This service defines the transaction boundary separate from HTTP fetching.
 */
@Service
public class RatePersistenceService {
    private static final Logger log = LoggerFactory.getLogger(RatePersistenceService.class);

    private final ExchangeRateRepository repository;

    public RatePersistenceService(ExchangeRateRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists exchange rates atomically using PostgreSQL upsert.
     * <p>
     * Transaction boundary: starts here, ends when method completes.
     * <p>
     * Uses native SQL INSERT ... ON CONFLICT to handle concurrent executions safely:
     * - No duplicate rows even under concurrent ingestion
     * - No unique constraint violations
     * - Existing rates can be updated if provider corrects values
     * <p>
     * Note: JPA lifecycle callbacks (@PrePersist, @PreUpdate) do not execute for native queries.
     * Timestamps are managed explicitly in the SQL.
     * <p>
     * Counting inserts vs updates requires checking existence before upsert, which introduces
     * a race condition window. For strict correctness, we check BEFORE the upsert to get a snapshot,
     * accepting that counts may not be 100% accurate under concurrent execution.
     * The database state remains consistent regardless.
     *
     * @param rateDate     the date of the rate snapshot
     * @param baseCurrency the base currency (e.g., EUR, USD)
     * @param rates        map of currency code to rate value (base currency included with rate=1)
     * @return RatePersistenceResult with approximate insert/update counts
     */
    @Transactional
    public RatePersistenceResult upsertRates(LocalDate rateDate, String baseCurrency, Map<String, BigDecimal> rates) {
        log.debug("Starting transactional upsert: date={}, base={}, currencies={}",
                rateDate, baseCurrency, rates.size());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int inserted = 0;
        int updated = 0;

        for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
            String currencyCode = entry.getKey();
            BigDecimal rateValue = entry.getValue();

            // Check existence BEFORE upsert (best-effort count)
            Long existingId = repository.findIdByNaturalKey(rateDate, baseCurrency, currencyCode);
            boolean existedBefore = (existingId != null);

            // Atomic upsert
            repository.upsertRate(
                    rateDate,
                    baseCurrency,
                    currencyCode,
                    rateValue,
                    now
            );

            if (existedBefore) {
                updated++;
            } else {
                inserted++;
            }
        }

        log.info("Transactional upsert complete: inserted={}, updated={}", inserted, updated);

        return new RatePersistenceResult(inserted, updated);
    }

    /**
     * Result of a transactional persistence operation.
     */
    public record RatePersistenceResult(int inserted, int updated) {
    }
}
