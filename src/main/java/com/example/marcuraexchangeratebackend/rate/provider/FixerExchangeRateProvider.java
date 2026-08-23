package com.example.marcuraexchangeratebackend.rate.provider;

import com.example.marcuraexchangeratebackend.common.config.FixerApiProperties;
import com.example.marcuraexchangeratebackend.rate.domain.RateSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;


/**
 * Fixer.io implementation of ExchangeRateProvider.
 * Fetches latest exchange rates from Fixer.io /latest endpoint.
 * <p>
 * Configuration via FixerApiProperties:
 * - fixer.api.base-url
 * - fixer.api.key
 * - fixer.api.timeout (milliseconds)
 * <p>
 * Handles:
 * - transport errors (connection timeout, DNS failure)
 * - non-2xx HTTP responses
 * - Fixer success=false responses
 * - missing required fields (base, date, rates)
 */
@Component
public class FixerExchangeRateProvider implements ExchangeRateProvider {
    private static final Logger log = LoggerFactory.getLogger(FixerExchangeRateProvider.class);
    private static final String LATEST_ENDPOINT = "/latest";

    private final RestClient restClient;
    private final FixerApiProperties properties;

    public FixerExchangeRateProvider(RestClient.Builder restClientBuilder, FixerApiProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new ExchangeRateProviderException(
                            String.format("Fixer API returned HTTP %d", response.getStatusCode().value())
                    );
                })
                .build();
    }

    @Override
    public RateSnapshot fetchLatestRates() {

        try {
            log.info("Fetching latest rates from Fixer.io: {}", properties.baseUrl() + LATEST_ENDPOINT);

            // Build actual URL with real API key (not logged)
            String actualUrl = String.format("%s?access_key=%s", LATEST_ENDPOINT, properties.key());

            FixerApiResponse response = restClient.get()
                    .uri(actualUrl)
                    .retrieve()
                    .body(FixerApiResponse.class);

            if (response == null) {
                throw new ExchangeRateProviderException("Fixer API returned null response");
            }

            // Handle Fixer success=false
            if (!response.success()) {
                FixerApiResponse.FixerErrorInfo error = response.error();
                String errorMessage = error != null && error.info() != null
                        ? error.info()
                        : "Unknown Fixer API error";
                Integer errorCode = error != null ? error.code() : null;
                String errorType = error != null ? error.type() : null;

                log.error("Fixer API returned success=false: code={}, type={}, message={}",
                        errorCode, errorType, errorMessage);

                throw new FixerApiException(errorMessage, errorCode, errorType);
            }

            // Validate required fields
            validateResponse(response);

            // Log concise metadata only (not full payload)
            log.info("Successfully fetched Fixer snapshot: date={}, base={}, currencies={}",
                    response.date(), response.base(), response.rates().size());

            return new RateSnapshot(
                    response.date(),
                    response.base(),
                    response.rates()
            );

        } catch (RestClientException e) {
            log.error("Transport error while fetching rates from Fixer.io", e);
            throw new ExchangeRateProviderException("Failed to fetch rates from Fixer.io: " + e.getMessage(), e);
        }
    }

    private void validateResponse(FixerApiResponse response) {
        if (response.base() == null || response.base().isBlank()) {
            throw new ExchangeRateProviderException("Fixer API response missing required field: base");
        }
        if (response.date() == null) {
            throw new ExchangeRateProviderException("Fixer API response missing required field: date");
        }
        if (response.rates() == null || response.rates().isEmpty()) {
            throw new ExchangeRateProviderException("Fixer API response missing required field: rates");
        }
    }
}
