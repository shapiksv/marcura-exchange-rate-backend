package com.example.marcuraexchangeratebackend.insight.domain;

/**
 * Deterministic trend direction for exchange rate movement.
 * <p>
 * Calculated by the application, NOT by the LLM.
 */
public enum TrendDirection {
    /**
     * Exchange rate increased from first to last data point.
     */
    INCREASE,

    /**
     * Exchange rate decreased from first to last data point.
     */
    DECREASE,

    /**
     * Exchange rate remained unchanged from first to last data point.
     */
    UNCHANGED
}
