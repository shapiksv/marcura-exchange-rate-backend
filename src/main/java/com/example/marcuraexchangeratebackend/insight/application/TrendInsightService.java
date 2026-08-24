package com.example.marcuraexchangeratebackend.insight.application;

import com.example.marcuraexchangeratebackend.common.error.RateNotFoundException;
import com.example.marcuraexchangeratebackend.exchange.api.HistoricalRateEntry;
import com.example.marcuraexchangeratebackend.exchange.application.ExchangeHistoryService;
import com.example.marcuraexchangeratebackend.insight.domain.TrendInsightContext;
import com.example.marcuraexchangeratebackend.insight.domain.TrendInsightGenerator;
import com.example.marcuraexchangeratebackend.insight.domain.TrendMetricsCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Application service for generating AI-powered trend insights.
 * <p>
 * Orchestrates:
 * 1. Fetching historical rate data from ExchangeHistoryService (read-only DB access)
 * 2. Calculating deterministic trend metrics using TrendMetricsCalculator
 * 3. Building TrendInsightContext with actual data + calculated metrics
 * 4. Delegating to TrendInsightGenerator (AI provider)
 * <p>
 * Does NOT increment usage counters.
 * <p>
 * Does NOT keep database transaction open during LLM call.
 */
@Service
public class TrendInsightService {

    private static final Logger log = LoggerFactory.getLogger(TrendInsightService.class);

    private static final String INSUFFICIENT_DATA_MESSAGE =
            "Insufficient historical data to determine a trend for the selected period.";

    private final ExchangeHistoryService exchangeHistoryService;
    private final TrendInsightGenerator trendInsightGenerator;
    private final TrendMetricsCalculator metricsCalculator;

    public TrendInsightService(
            ExchangeHistoryService exchangeHistoryService,
            TrendInsightGenerator trendInsightGenerator,
            TrendMetricsCalculator metricsCalculator
    ) {
        this.exchangeHistoryService = exchangeHistoryService;
        this.trendInsightGenerator = trendInsightGenerator;
        this.metricsCalculator = metricsCalculator;
    }

    /**
     * Generate an AI-powered trend insight for a currency pair within a date range.
     * <p>
     * Flow:
     * 1. Fetch historical rates (read-only transaction completes here)
     * 2. Validate sufficient data exists
     * 3. Calculate deterministic trend metrics (direction, percentage change, etc.)
     * 4. Build context with actual historical values + calculated metrics
     * 5. Call AI provider (no transaction)
     * <p>
     * Does NOT increment usage counters.
     *
     * @param from     source currency code
     * @param to       target currency code
     * @param fromDate start of date range (inclusive)
     * @param toDate   end of date range (inclusive)
     * @return AI-generated trend insight text
     * @throws RateNotFoundException if no historical data exists in the selected range
     */
    public String generateTrendInsight(
            String from,
            String to,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        log.debug("Generating trend insight for {}/{} from {} to {}",
                from, to, fromDate, toDate);

        // Step 1: Fetch historical data (read-only DB transaction completes here)
        List<HistoricalRateEntry> historicalRates = exchangeHistoryService.getHistoricalRates(
                from, to, fromDate, toDate
        );

        // Step 2: Validate data availability
        if (historicalRates.isEmpty()) {
            log.warn("No historical data available for {}/{} in range {} to {}",
                    from, to, fromDate, toDate);
            throw new RateNotFoundException(
                    String.format("No historical rates available for %s/%s in the selected period",
                            from, to)
            );
        }

        if (historicalRates.size() < 2) {
            log.info("Insufficient data points ({}) for {}/{}, returning deterministic message",
                    historicalRates.size(), from, to);
            return INSUFFICIENT_DATA_MESSAGE;
        }

        // Step 3: Build context with actual historical values + calculate metrics
        TrendInsightContext context = buildContext(from, to, fromDate, toDate, historicalRates);

        // Step 4: Call AI provider (no database transaction here)
        String insight = trendInsightGenerator.generateInsight(context);

        log.debug("Generated insight for {}/{}: {} characters", from, to, insight.length());

        return insight;
    }

    /**
     * Build immutable TrendInsightContext from historical rate entries.
     * <p>
     * Uses raw rates (not adjusted) as these are the values shown in the historical chart.
     * <p>
     * Calculates deterministic trend metrics using TrendMetricsCalculator.
     */
    private TrendInsightContext buildContext(
            String from,
            String to,
            LocalDate fromDate,
            LocalDate toDate,
            List<HistoricalRateEntry> historicalRates
    ) {
        List<TrendInsightContext.HistoricalDataPoint> dataPoints = historicalRates.stream()
                .map(entry -> new TrendInsightContext.HistoricalDataPoint(
                        entry.date(),
                        entry.rawRate()
                ))
                .toList();

        // Calculate deterministic trend metrics
        TrendInsightContext.TrendMetrics metrics = metricsCalculator.calculateMetrics(dataPoints);

        return new TrendInsightContext(
                from,
                to,
                fromDate,
                toDate,
                dataPoints,
                metrics
        );
    }
}
