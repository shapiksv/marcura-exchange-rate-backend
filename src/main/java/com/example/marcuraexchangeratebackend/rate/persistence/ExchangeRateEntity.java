package com.example.marcuraexchangeratebackend.rate.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Entity representing a currency exchange rate from Fixer.io.
 *
 * Stores the rate value for a specific currency against a base currency on a particular date.
 * The unique constraint ensures no duplicates for (rate_date, base_currency, currency_code).
 */
@Entity
@Table(name = "exchange_rate",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exchange_rate_date_base_currency",
                columnNames = {"rate_date", "base_currency", "currency_code"}
        ),
        indexes = {
                @Index(name = "idx_exchange_rate_date", columnList = "rate_date"),
                @Index(name = "idx_exchange_rate_currency_date", columnList = "currency_code, rate_date"),
                @Index(name = "idx_exchange_rate_base_currency_date", columnList = "base_currency, currency_code, rate_date")
        }
)
public class ExchangeRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "rate_value", nullable = false, precision = 19, scale = 10)
    private BigDecimal rateValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    protected ExchangeRateEntity() {
        // JPA requires a no-arg constructor
    }

    public ExchangeRateEntity(LocalDate rateDate, String baseCurrency, String currencyCode, BigDecimal rateValue) {
        this.rateDate = Objects.requireNonNull(rateDate, "rateDate must not be null");
        this.baseCurrency = Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        this.rateValue = Objects.requireNonNull(rateValue, "rateValue must not be null");
    }

    // Getters
    public Long getId() {
        return id;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getRateValue() {
        return rateValue;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Setters for update scenarios
    public void setRateValue(BigDecimal rateValue) {
        this.rateValue = Objects.requireNonNull(rateValue, "rateValue must not be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExchangeRateEntity that)) return false;
        return Objects.equals(rateDate, that.rateDate) &&
                Objects.equals(baseCurrency, that.baseCurrency) &&
                Objects.equals(currencyCode, that.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rateDate, baseCurrency, currencyCode);
    }

    @Override
    public String toString() {
        return "ExchangeRateEntity{" +
                "id=" + id +
                ", rateDate=" + rateDate +
                ", baseCurrency='" + baseCurrency + '\'' +
                ", currencyCode='" + currencyCode + '\'' +
                ", rateValue=" + rateValue +
                '}';
    }
}
