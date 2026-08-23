package com.example.marcuraexchangeratebackend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Fixer.io API integration.
 */
@ConfigurationProperties(prefix = "fixer.api")
public record FixerApiProperties(
        String baseUrl,
        String key,
        Integer timeout
) {
}
