package com.example.marcuraexchangeratebackend.analytics.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Analytics response containing usage statistics.
 * <p>
 * Provides data for the analytics dashboard:
 * - Top currencies by total query count
 * - Daily usage breakdown
 */
@Schema(description = "Currency usage analytics data")
public record AnalyticsResponse(
        @Schema(description = "Top currencies ordered by total query count (descending)")
        List<CurrencyUsageSummary> topCurrencies,

        @Schema(description = "Daily usage records ordered by date and currency")
        List<DailyUsageEntry> dailyUsage
) {
}
