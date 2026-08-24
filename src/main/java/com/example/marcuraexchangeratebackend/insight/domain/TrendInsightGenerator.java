package com.example.marcuraexchangeratebackend.insight.domain;

/**
 * AI boundary interface for generating trend insights.
 * <p>
 * Abstracts the underlying LLM provider (Ollama, OpenAI, etc.) from application logic.
 * <p>
 * Similar architectural role to ExchangeRateProvider for external Fixer.io integration.
 */
public interface TrendInsightGenerator {

    /**
     * Generate a concise natural-language trend insight from historical rate context.
     * <p>
     * The implementation must use the actual historical values from the context.
     * <p>
     * The insight must be concise (maximum 2 short sentences) and based only on supplied data.
     *
     * @param context immutable context containing currency pair, date range, and historical rates
     * @return concise trend insight text
     * @throws AiProviderException if the underlying AI provider is unavailable or fails
     */
    String generateInsight(TrendInsightContext context);
}
