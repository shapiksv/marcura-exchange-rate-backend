package com.example.marcuraexchangeratebackend.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenApiConfigTest {

    @Test
    void testOpenApiConfigBeanCreation() {
        OpenApiConfig config = new OpenApiConfig();
        assertNotNull(config.exchangeRateOpenAPI());
        assertNotNull(config.exchangeRateOpenAPI().getInfo());
        assertNotNull(config.exchangeRateOpenAPI().getInfo().getTitle());
    }
}
