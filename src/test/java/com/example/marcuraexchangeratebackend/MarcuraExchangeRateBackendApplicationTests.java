package com.example.marcuraexchangeratebackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test that verifies the Spring Boot application context loads successfully.
 * Uses PostgreSQL Testcontainers to provide a real database for the test.
 * Prerequisites:
 * - Docker must be running locally
 * - Database schema will be created by Liquibase in Phase 1
 * If Docker is not available, this test will be skipped automatically by Testcontainers.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MarcuraExchangeRateBackendApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Test
    void contextLoads() {
        // This test verifies that the Spring Boot application context
        // can be loaded successfully with a PostgreSQL database.
        // Will pass once Phase 1 schema migrations are in place.
    }
}
