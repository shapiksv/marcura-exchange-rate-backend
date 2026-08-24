package com.example.marcuraexchangeratebackend.analytics.api;

import com.example.marcuraexchangeratebackend.analytics.application.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for currency usage analytics.
 * <p>
 * Provides read-only access to usage statistics without affecting usage counters.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Currency usage analytics API")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Get currency usage analytics.
     *
     * @return analytics data including top currencies and daily usage breakdown
     */
    @GetMapping
    @Operation(
            summary = "Get currency usage analytics",
            description = "Returns aggregated usage statistics: " +
                    "top currencies by total query count, and daily usage breakdown. " +
                    "Does NOT increment usage counters. " +
                    "Returns empty arrays if no usage data exists.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Analytics data retrieved successfully",
                            content = @Content(schema = @Schema(implementation = AnalyticsResponse.class))
                    )
            }
    )
    public ResponseEntity<AnalyticsResponse> getAnalytics() {
        List<CurrencyUsageSummary> topCurrencies = analyticsService.getTopCurrencies();
        List<DailyUsageEntry> dailyUsage = analyticsService.getDailyUsage();

        AnalyticsResponse response = new AnalyticsResponse(topCurrencies, dailyUsage);

        return ResponseEntity.ok(response);
    }
}
