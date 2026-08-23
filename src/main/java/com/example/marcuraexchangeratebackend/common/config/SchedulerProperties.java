package com.example.marcuraexchangeratebackend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the rate collection scheduler.
 */
@ConfigurationProperties(prefix = "scheduler.rate-collection")
public record SchedulerProperties(
        String cron,
        String zone
) {
}
