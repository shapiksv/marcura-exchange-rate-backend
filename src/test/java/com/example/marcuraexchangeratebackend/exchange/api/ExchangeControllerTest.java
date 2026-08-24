package com.example.marcuraexchangeratebackend.exchange.api;

import com.example.marcuraexchangeratebackend.exchange.application.ExchangeApplicationService;
import com.example.marcuraexchangeratebackend.exchange.application.ExchangeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ExchangeController.
 * <p>
 * Tests controller as plain Java object without Spring MVC infrastructure.
 * HTTP layer semantics (validation, error handling) are tested via integration tests.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeControllerTest {

    @Mock
    private ExchangeApplicationService exchangeService;

    private ExchangeController controller;

    @BeforeEach
    void setUp() {
        controller = new ExchangeController(exchangeService);
    }

    @Test
    @DisplayName("Should calculate exchange rate successfully")
    void shouldCalculateExchangeSuccessfully() {
        // Given
        String from = "EUR";
        String to = "PLN";
        LocalDate date = LocalDate.of(2024, 3, 15);

        ExchangeResult serviceResult = new ExchangeResult(
                from,
                to,
                new BigDecimal("4.4405487565413254"),
                date,
                142L,
                37L
        );

        when(exchangeService.calculateExchange(from, to, date))
                .thenReturn(serviceResult);

        // When
        ResponseEntity<ExchangeResponse> response = controller.calculateExchange(from, to, date);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().from()).isEqualTo(from);
        assertThat(response.getBody().to()).isEqualTo(to);
        assertThat(response.getBody().exchange()).isEqualTo(new BigDecimal("4.4405487565413254"));
        assertThat(response.getBody().date()).isEqualTo(date);
        assertThat(response.getBody().fromQueryCount()).isEqualTo(142L);
        assertThat(response.getBody().toQueryCount()).isEqualTo(37L);

        verify(exchangeService).calculateExchange(from, to, date);
    }

    @Test
    @DisplayName("Should calculate exchange rate with null date (latest)")
    void shouldCalculateExchangeWithNullDate() {
        // Given
        String from = "USD";
        String to = "GBP";
        LocalDate latestDate = LocalDate.of(2024, 3, 20);

        ExchangeResult serviceResult = new ExchangeResult(
                from,
                to,
                new BigDecimal("0.7845623"),
                latestDate,
                58L,
                91L
        );

        when(exchangeService.calculateExchange(from, to, null))
                .thenReturn(serviceResult);

        // When
        ResponseEntity<ExchangeResponse> response = controller.calculateExchange(from, to, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().from()).isEqualTo(from);
        assertThat(response.getBody().to()).isEqualTo(to);
        assertThat(response.getBody().exchange()).isEqualTo(new BigDecimal("0.7845623"));
        assertThat(response.getBody().date()).isEqualTo(latestDate);
        assertThat(response.getBody().fromQueryCount()).isEqualTo(58L);
        assertThat(response.getBody().toQueryCount()).isEqualTo(91L);

        verify(exchangeService).calculateExchange(from, to, null);
    }

    @Test
    @DisplayName("Should delegate to service for same currency request")
    void shouldDelegateToServiceForSameCurrency() {
        // Given
        String currency = "EUR";
        LocalDate date = LocalDate.of(2024, 3, 15);

        ExchangeResult serviceResult = new ExchangeResult(
                currency,
                currency,
                BigDecimal.ONE,
                date,
                10L,
                10L
        );

        when(exchangeService.calculateExchange(currency, currency, date))
                .thenReturn(serviceResult);

        // When
        ResponseEntity<ExchangeResponse> response = controller.calculateExchange(currency, currency, date);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().from()).isEqualTo(currency);
        assertThat(response.getBody().to()).isEqualTo(currency);
        assertThat(response.getBody().exchange()).isEqualTo(BigDecimal.ONE);

        verify(exchangeService).calculateExchange(currency, currency, date);
    }

    @Test
    @DisplayName("Should map service result to response correctly")
    void shouldMapServiceResultToResponseCorrectly() {
        // Given
        ExchangeResult serviceResult = new ExchangeResult(
                "JPY",
                "KRW",
                new BigDecimal("9.3764285714285714"),
                LocalDate.of(2024, 3, 10),
                5L,
                12L
        );

        when(exchangeService.calculateExchange("JPY", "KRW", LocalDate.of(2024, 3, 10)))
                .thenReturn(serviceResult);

        // When
        ResponseEntity<ExchangeResponse> response = controller.calculateExchange(
                "JPY",
                "KRW",
                LocalDate.of(2024, 3, 10)
        );

        // Then
        ExchangeResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.from()).isEqualTo(serviceResult.from());
        assertThat(body.to()).isEqualTo(serviceResult.to());
        assertThat(body.exchange()).isEqualByComparingTo(serviceResult.exchange());
        assertThat(body.date()).isEqualTo(serviceResult.date());
        assertThat(body.fromQueryCount()).isEqualTo(serviceResult.fromQueryCount());
        assertThat(body.toQueryCount()).isEqualTo(serviceResult.toQueryCount());
    }

    @Test
    @DisplayName("Should preserve BigDecimal precision in response")
    void shouldPreserveBigDecimalPrecisionInResponse() {
        // Given
        BigDecimal preciseRate = new BigDecimal("1.2345678901234567890123456789");
        ExchangeResult serviceResult = new ExchangeResult(
                "EUR",
                "USD",
                preciseRate,
                LocalDate.of(2024, 3, 15),
                100L,
                200L
        );

        when(exchangeService.calculateExchange("EUR", "USD", null))
                .thenReturn(serviceResult);

        // When
        ResponseEntity<ExchangeResponse> response = controller.calculateExchange("EUR", "USD", null);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().exchange())
                .isEqualByComparingTo(preciseRate);
    }
}
