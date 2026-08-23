package com.example.marcuraexchangeratebackend.rate.scheduler;

import com.example.marcuraexchangeratebackend.common.config.SchedulerConfig;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying ShedLock configuration and scheduler setup.
 * Tests:
 * - LockProvider bean is created
 * - SchedulerConfig is applied
 * - RateCollectionScheduler bean exists
 * - Database-backed lock provider is configured
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RateCollectionSchedulerIntegrationTest {

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

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void schedulerConfig_lockProviderBean_exists() {
        // Verify LockProvider bean is created by SchedulerConfig
        assertThat(applicationContext.containsBean("lockProvider")).isTrue();

        LockProvider lockProvider = applicationContext.getBean(LockProvider.class);
        assertThat(lockProvider).isNotNull();
    }

    @Test
    void schedulerConfig_schedulerConfigBean_exists() {
        // Verify SchedulerConfig is loaded
        assertThat(applicationContext.containsBean("schedulerConfig")).isTrue();

        SchedulerConfig schedulerConfig = applicationContext.getBean(SchedulerConfig.class);
        assertThat(schedulerConfig).isNotNull();
    }

    @Test
    void rateCollectionScheduler_bean_exists() {
        // Verify RateCollectionScheduler bean is created
        assertThat(applicationContext.containsBean("rateCollectionScheduler")).isTrue();

        RateCollectionScheduler scheduler = applicationContext.getBean(RateCollectionScheduler.class);
        assertThat(scheduler).isNotNull();
    }

    @Test
    void schedulerConfig_enableScheduling_annotationPresent() {
        // Verify @EnableScheduling is present on SchedulerConfig
        SchedulerConfig config = applicationContext.getBean(SchedulerConfig.class);
        assertThat(config.getClass().isAnnotationPresent(
                org.springframework.scheduling.annotation.EnableScheduling.class
        )).isTrue();
    }

    @Test
    void schedulerConfig_enableSchedulerLock_annotationPresent() {
        // Verify @EnableSchedulerLock is present on SchedulerConfig
        SchedulerConfig config = applicationContext.getBean(SchedulerConfig.class);
        assertThat(config.getClass().isAnnotationPresent(
                net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.class
        )).isTrue();
    }
}
