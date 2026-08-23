package com.example.marcuraexchangeratebackend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI exchangeRateOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Marcura Exchange Rate Management API")
                        .description("REST API for exchange rate calculation, analytics, and AI-powered insights")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Marcura Exchange Rate System")));
    }
}
