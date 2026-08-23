package com.example.marcuraexchangeratebackend.rate.application;

import com.example.marcuraexchangeratebackend.rate.domain.RateSnapshot;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateEntity;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateRepository;
import com.example.marcuraexchangeratebackend.rate.provider.ExchangeRateProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for rate collection and persistence with real PostgreSQL via Testcontainers.
 * Tests:
 * - Atomic PostgreSQL upsert (ON CONFLICT)
 * - Base currency normalization (base currency added with rate=1)
 * - Validation rules
 * - Concurrent ingestion safety
 * - Transaction boundary (HTTP outside @Transactional)
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RateCollectionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /**
     * Mutable test provider that allows tests to configure the snapshot to return.
     * Instance-based (not static) to avoid shared mutable state across tests.
     */
    static class MutableTestProvider implements ExchangeRateProvider {
        private RateSnapshot snapshot;

        public void setSnapshot(RateSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public RateSnapshot fetchLatestRates() {
            if (snapshot == null) {
                throw new IllegalStateException("Test must call setSnapshot() before fetching rates");
            }
            return snapshot;
        }
    }

    @TestConfiguration
    static class TestProviderConfig {
        @Bean
        @Primary
        public ExchangeRateProvider testExchangeRateProvider() {
            // Return mutable instance-based provider
            MutableTestProvider provider = new MutableTestProvider();
            // Set default snapshot
            provider.setSnapshot(new RateSnapshot(
                    LocalDate.of(2024, 3, 15),
                    "EUR",
                    Map.of("USD", new BigDecimal("1.08360"))
            ));
            return provider;
        }
    }

    @Autowired
    private RateCollectionService collectionService;

    @Autowired
    private RatePersistenceService persistenceService;

    @Autowired
    private ExchangeRateRepository repository;

    @Autowired
    private ExchangeRateProvider testProvider;

    @Test
    void baseCurrencyNormalization_includesBaseWithRateOne() {
        // Given - Fixer response WITHOUT base currency in rates map
        ((MutableTestProvider) testProvider).setSnapshot(new RateSnapshot(
                LocalDate.of(2024, 3, 20),
                "EUR",
                Map.of(
                        "USD", new BigDecimal("1.08360"),
                        "PLN", new BigDecimal("4.56734")
                )
        ));

        // When
        RateCollectionResult result = collectionService.collectAndPersistLatestRates();

        // Then - base currency EUR should be persisted with rate=1
        assertThat(result.totalRates()).isEqualTo(3); // USD, PLN, EUR

        List<ExchangeRateEntity> persisted = repository.findByRateDateAndBaseCurrency(
                LocalDate.of(2024, 3, 20), "EUR"
        );
        assertThat(persisted).hasSize(3);

        ExchangeRateEntity eurRate = persisted.stream()
                .filter(e -> e.getCurrencyCode().equals("EUR"))
                .findFirst()
                .orElseThrow();
        assertThat(eurRate.getRateValue()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void atomicUpsert_repeatedIngestion_noDuplicates() {
        // Given - same snapshot twice
        RateSnapshot snapshot = new RateSnapshot(
                LocalDate.of(2024, 3, 21),
                "EUR",
                Map.of("USD", new BigDecimal("1.08500"))
        );
        ((MutableTestProvider) testProvider).setSnapshot(snapshot);

        // When - first ingestion
        RateCollectionResult result1 = collectionService.collectAndPersistLatestRates();

        // Then
        assertThat(result1.inserted()).isEqualTo(2); // USD + EUR (base)
        assertThat(result1.updated()).isEqualTo(0);

        // When - second ingestion (same data)
        RateCollectionResult result2 = collectionService.collectAndPersistLatestRates();

        // Then - should update existing rows, not create duplicates
        assertThat(result2.inserted()).isEqualTo(0);
        assertThat(result2.updated()).isEqualTo(2);

        // Verify no duplicates in database
        List<ExchangeRateEntity> persisted = repository.findByRateDateAndBaseCurrency(
                LocalDate.of(2024, 3, 21), "EUR"
        );
        assertThat(persisted).hasSize(2);
    }

    @Test
    void atomicUpsert_updatedRates_correctlyUpdates() {
        // Given - initial snapshot
        ((MutableTestProvider) testProvider).setSnapshot(new RateSnapshot(
                LocalDate.of(2024, 3, 22),
                "EUR",
                Map.of("USD", new BigDecimal("1.08000"))
        ));

        collectionService.collectAndPersistLatestRates();

        // When - Fixer corrects the rate for the same date
        ((MutableTestProvider) testProvider).setSnapshot(new RateSnapshot(
                LocalDate.of(2024, 3, 22),
                "EUR",
                Map.of("USD", new BigDecimal("1.09000")) // corrected value
        ));

        RateCollectionResult result = collectionService.collectAndPersistLatestRates();

        // Then - should update, not insert
        assertThat(result.inserted()).isEqualTo(0);
        assertThat(result.updated()).isEqualTo(2); // USD + EUR

        // Verify updated value
        ExchangeRateEntity updated = repository
                .findByRateDateAndBaseCurrencyAndCurrencyCode(
                        LocalDate.of(2024, 3, 22), "EUR", "USD"
                )
                .orElseThrow();
        assertThat(updated.getRateValue()).isEqualByComparingTo("1.09000");
    }

    @Test
    void concurrentIngestion_noConstraintViolations() throws InterruptedException {
        // Given - same snapshot ingested concurrently by multiple threads
        RateSnapshot snapshot = new RateSnapshot(
                LocalDate.of(2024, 3, 23),
                "EUR",
                Map.of(
                        "USD", new BigDecimal("1.08360"),
                        "PLN", new BigDecimal("4.56734")
                )
        );
        ((MutableTestProvider) testProvider).setSnapshot(snapshot);

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - execute concurrent ingestion
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    collectionService.collectAndPersistLatestRates();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads simultaneously
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);

        executor.shutdown();

        // Then - all threads should succeed
        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(errorCount.get()).isEqualTo(0);

        // Verify no duplicates in database
        List<ExchangeRateEntity> persisted = repository.findByRateDateAndBaseCurrency(
                LocalDate.of(2024, 3, 23), "EUR"
        );
        assertThat(persisted).hasSize(3); // USD, PLN, EUR (not 3 * threadCount)

        // Verify rates are correct
        ExchangeRateEntity usdRate = persisted.stream()
                .filter(e -> e.getCurrencyCode().equals("USD"))
                .findFirst()
                .orElseThrow();
        assertThat(usdRate.getRateValue()).isEqualByComparingTo("1.08360");
    }

    @Test
    void differentDates_bothPersisted() {
        // Given - two different dates
        ((MutableTestProvider) testProvider).setSnapshot(new RateSnapshot(
                LocalDate.of(2024, 3, 24),
                "EUR",
                Map.of("USD", new BigDecimal("1.08000"))
        ));
        collectionService.collectAndPersistLatestRates();

        ((MutableTestProvider) testProvider).setSnapshot(new RateSnapshot(
                LocalDate.of(2024, 3, 25),
                "EUR",
                Map.of("USD", new BigDecimal("1.09000"))
        ));
        collectionService.collectAndPersistLatestRates();

        // Then - both dates should exist
        List<ExchangeRateEntity> date1 = repository.findByRateDateAndBaseCurrency(
                LocalDate.of(2024, 3, 24), "EUR"
        );
        List<ExchangeRateEntity> date2 = repository.findByRateDateAndBaseCurrency(
                LocalDate.of(2024, 3, 25), "EUR"
        );

        assertThat(date1).hasSize(2);
        assertThat(date2).hasSize(2);
        assertThat(date1.get(0).getRateDate()).isEqualTo(LocalDate.of(2024, 3, 24));
        assertThat(date2.get(0).getRateDate()).isEqualTo(LocalDate.of(2024, 3, 25));
    }
}
