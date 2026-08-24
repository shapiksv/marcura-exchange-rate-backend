package com.example.marcuraexchangeratebackend.rate.persistence;

import java.time.LocalDate;

/**
 * Projection for common snapshot query results.
 * <p>
 * Represents a snapshot (rate_date, base_currency) that contains both required currencies.
 * <p>
 * Used by snapshot resolution queries to avoid raw Object[] results and provide type safety.
 */
public interface CommonSnapshotProjection {
    LocalDate getRateDate();
    String getBaseCurrency();
}
