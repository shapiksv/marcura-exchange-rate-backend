package com.example.marcuraexchangeratebackend.analytics.persistence;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Entity for tracking currency usage on a per-day basis.
 *
 * Uses atomic PostgreSQL upsert to safely increment query counts under concurrent access.
 * The unique constraint on (currency_code, query_date) ensures one row per currency per day.
 */
@Entity
@Table(name = "currency_usage_daily",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_currency_usage_daily_currency_date",
                columnNames = {"currency_code", "query_date"}
        ),
        indexes = {
                @Index(name = "idx_currency_usage_daily_date", columnList = "query_date")
        }
)
public class CurrencyUsageDailyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "query_date", nullable = false)
    private LocalDate queryDate;

    @Column(name = "query_count", nullable = false)
    private Long queryCount = 0L;

    @Column(name = "last_queried_at", nullable = false)
    private OffsetDateTime lastQueriedAt;

    protected CurrencyUsageDailyEntity() {
        // JPA requires a no-arg constructor
    }

    public CurrencyUsageDailyEntity(String currencyCode, LocalDate queryDate, OffsetDateTime lastQueriedAt) {
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        this.queryDate = Objects.requireNonNull(queryDate, "queryDate must not be null");
        this.lastQueriedAt = Objects.requireNonNull(lastQueriedAt, "lastQueriedAt must not be null");
        this.queryCount = 1L;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LocalDate getQueryDate() {
        return queryDate;
    }

    public Long getQueryCount() {
        return queryCount;
    }

    public OffsetDateTime getLastQueriedAt() {
        return lastQueriedAt;
    }

    // For update scenarios
    public void setQueryCount(Long queryCount) {
        this.queryCount = queryCount;
    }

    public void setLastQueriedAt(OffsetDateTime lastQueriedAt) {
        this.lastQueriedAt = lastQueriedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CurrencyUsageDailyEntity that)) return false;
        return Objects.equals(currencyCode, that.currencyCode) &&
                Objects.equals(queryDate, that.queryDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currencyCode, queryDate);
    }

    @Override
    public String toString() {
        return "CurrencyUsageDailyEntity{" +
                "id=" + id +
                ", currencyCode='" + currencyCode + '\'' +
                ", queryDate=" + queryDate +
                ", queryCount=" + queryCount +
                ", lastQueriedAt=" + lastQueriedAt +
                '}';
    }
}
