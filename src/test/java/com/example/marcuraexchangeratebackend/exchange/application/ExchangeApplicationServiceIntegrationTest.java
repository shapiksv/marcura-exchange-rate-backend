package com.example.marcuraexchangeratebackend.exchange.application;

import com.example.marcuraexchangeratebackend.analytics.persistence.CurrencyUsageDailyEntity;
import com.example.marcuraexchangeratebackend.analytics.persistence.CurrencyUsageDailyRepository;
import com.example.marcuraexchangeratebackend.common.error.RateNotFoundException;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateEntity;
import com.example.marcuraexchangeratebackend.rate.persistence.ExchangeRateRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for ExchangeApplicationService with real PostgreSQL database.
 * <p>
 * These tests verify:
 * - Concurrent usage counter increments
 * - Transaction boundaries
 * - Database-level atomic operations
 * <p>
 * Tests are disabled if Docker/Testcontainers is unavailable.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=true",
        "spring.ai.ollama.chat.enabled=false"
})
class ExchangeApplicationServiceIntegrationTest {

    private static PostgreSQLContainer<?> postgres;

    @Autowired
    private ExchangeApplicationService exchangeService;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private CurrencyUsageDailyRepository usageRepository;

    private static final LocalDate TEST_DATE = LocalDate.of(2024, 3, 15);
    private static final String BASE_CURRENCY = "EUR";

    @BeforeAll
    static void startPostgres() {
        assumeTrue(isDockerAvailable(), "Docker is not available");

        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        System.setProperty("spring.datasource.url", postgres.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgres.getUsername());
        System.setProperty("spring.datasource.password", postgres.getPassword());
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // Clean up test data
        usageRepository.deleteAll();
        exchangeRateRepository.deleteAll();
    }

    @Test
    void calculateExchange_withRealDatabase_success() {
        // Arrange
        exchangeRateRepository.save(new ExchangeRateEntity(TEST_DATE, BASE_CURRENCY, "EUR", BigDecimal.ONE));
        exchangeRateRepository.save(new ExchangeRateEntity(TEST_DATE, BASE_CURRENCY, "PLN", new BigDecimal("4.56734")));

        // Act
        ExchangeResult result = exchangeService.calculateExchange("EUR", "PLN", TEST_DATE);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.from()).isEqualTo("EUR");
        assertThat(result.to()).isEqualTo("PLN");
        assertThat(result.date()).isEqualTo(TEST_DATE);
        assertThat(result.exchange()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.fromQueryCount()).isEqualTo(1L);
        assertThat(result.toQueryCount()).isEqualTo(1L);

        // Verify usage records were created
        assertThat(usageRepository.count()).isEqualTo(2L);
    }

    @Test
    void calculateExchange_concurrentRequests_incrementsCorrectly() throws Exception {
        // Arrange
        exchangeRateRepository.save(new ExchangeRateEntity(TEST_DATE, BASE_CURRENCY, "EUR", BigDecimal.ONE));
        exchangeRateRepository.save(new ExchangeRateEntity(TEST_DATE, BASE_CURRENCY, "USD", new BigDecimal("1.0836")));

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Act - simulate concurrent requests
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    exchangeService.calculateExchange("EUR", "USD", TEST_DATE);
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
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);

        executor.shutdown();

        // Assert
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(errorCount.get()).isZero();

        // Verify usage counts are correct (no lost increments)
        Long eurCount = usageRepository.getTotalQueryCountForCurrency("EUR");
        Long usdCount = usageRepository.getTotalQueryCountForCurrency("USD");

        assertThat(eurCount).isEqualTo((long) threadCount);
        assertThat(usdCount).isEqualTo((long) threadCount);
    }

    @Test
    void calculateExchange_sameCurrency_incrementsTwice() {
        // Arrange
        exchangeRateRepository.save(new ExchangeRateEntity(TEST_DATE, BASE_CURRENCY, "EUR", BigDecimal.ONE));

        // Act
        ExchangeResult result = exchangeService.calculateExchange("EUR", "EUR", TEST_DATE);

        // Assert
        assertThat(result.exchange()).isEqualByComparingTo(BigDecimal.ONE);

        // Verify EUR incremented twice
        Long eurCount = usageRepository.getTotalQueryCountForCurrency("EUR");
        assertThat(eurCount).isEqualTo(2L);

        // But only one record exists (same currency, same date)
        assertThat(usageRepository.count()).isEqualTo(1L);

        CurrencyUsageDailyEntity eurUsage = usageRepository
                .findByCurrencyCodeAndQueryDate("EUR", LocalDate.now())
                .orElseThrow();
        assertThat(eurUsage.getQueryCount()).isEqualTo(2L);
    }

    @Test
    void calculateExchange_rateNotFound_doesNotIncrementUsage() {
        // Arrange - EUR exists, PLN does not
        exchangeRateRepository.save(new ExchangeRateEntity(TEST_DATE, BASE_CURRENCY, "EUR", BigDecimal.ONE));

        // Act & Assert
        assertThatThrownBy(() -> exchangeService.calculateExchange("EUR", "PLN", TEST_DATE))
                .isInstanceOf(RateNotFoundException.class);

        // Verify no usage records were created
        assertThat(usageRepository.count()).isZero();
    }

    @Test
    void calculateExchange_repeatedRequests_accumulatesCount() {
        // Arrange
        exchangeRateRepository.save(new ExchangeRateEntity(TEST_DATE, BASE_CURRENCY, "EUR", BigDecimal.ONE));
        exchangeRateRepository.save(new ExchangeRateEntity(TEST_DATE, BASE_CURRENCY, "GBP", new BigDecimal("0.8573")));

        // Act - make 3 requests
        for (int i = 0; i < 3; i++) {
            ExchangeResult result = exchangeService.calculateExchange("EUR", "GBP", TEST_DATE);
            assertThat(result.fromQueryCount()).isEqualTo((long) (i + 1));
            assertThat(result.toQueryCount()).isEqualTo((long) (i + 1));
        }

        // Assert final counts
        Long eurCount = usageRepository.getTotalQueryCountForCurrency("EUR");
        Long gbpCount = usageRepository.getTotalQueryCountForCurrency("GBP");

        assertThat(eurCount).isEqualTo(3L);
        assertThat(gbpCount).isEqualTo(3L);
    }

    private static boolean isDockerAvailable() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", "info");
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
