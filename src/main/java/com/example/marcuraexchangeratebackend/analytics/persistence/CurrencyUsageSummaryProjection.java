package com.example.marcuraexchangeratebackend.analytics.persistence;

import java.time.OffsetDateTime;

/**
 * Projection for aggregated currency usage summary.
 * <p>
 * Contains per-currency aggregation: total query count and last queried timestamp.
 * <p>
 * Used by analytics queries to avoid raw Object[] results and provide type safety.
 * <p>
 * Note: lastQueried returns String because PostgreSQL native queries with TIMESTAMP WITH TIME ZONE
 * return types that Spring Data projections cannot automatically convert to OffsetDateTime.
 * The service layer handles parsing the ISO 8601 string to OffsetDateTime.
 */
public interface CurrencyUsageSummaryProjection {
    String getCurrencyCode();
    Long getTotalCount();
    String getLastQueried();  // Returns ISO 8601 string, converted in service layer
}
