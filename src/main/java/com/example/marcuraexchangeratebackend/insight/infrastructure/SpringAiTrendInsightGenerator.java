package com.example.marcuraexchangeratebackend.insight.infrastructure;

import com.example.marcuraexchangeratebackend.insight.domain.AiProviderException;
import com.example.marcuraexchangeratebackend.insight.domain.TrendInsightContext;
import com.example.marcuraexchangeratebackend.insight.domain.TrendInsightGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Spring AI implementation of TrendInsightGenerator using Ollama (or any configured ChatModel).
 * <p>
 * Constructs a deliberate system prompt constraining the LLM to supplied data only.
 * <p>
 * Injects actual historical rate values into the user prompt.
 */
@Component
public class SpringAiTrendInsightGenerator implements TrendInsightGenerator {

    private static final Logger log = LoggerFactory.getLogger(SpringAiTrendInsightGenerator.class);

    static final String SYSTEM_PROMPT = """
            You are a concise exchange-rate trend analyst.
            
            The application has already calculated all numerical metrics for you.
            These application-calculated metrics are AUTHORITATIVE and MUST NOT be recalculated or contradicted.
            
            Rules:
            - Use ONLY the application-calculated direction and percentage change provided.
            - Do NOT recalculate percentage change yourself.
            - Do NOT recalculate direction yourself.
            - Do NOT contradict the supplied direction (INCREASE/DECREASE/UNCHANGED).
            - Do NOT contradict the supplied numerical metrics.
            - Use only the provided historical data points.
            - Do not invent news, causes, geopolitical events, or market explanations.
            - Do not provide financial advice.
            - Do not make unsupported predictions.
            - Do not claim knowledge outside the supplied dataset.
            - Describe only observable movement in the selected period using the supplied metrics.
            - When describing absolute change, state that it is the difference between the first and last available rates in the selected period.
            - Do NOT describe the first or last available point as "previous day's close", "daily close", "opening rate", or similar market concepts unless explicitly provided.
            - Do NOT infer that historical points are consecutive days.
            - Do NOT invent information about missing dates.
            - Keep the response concise: maximum 2 short sentences.
            - If the data is insufficient to identify a meaningful trend, say so clearly.
            """;

    private final ChatClient chatClient;

    public SpringAiTrendInsightGenerator(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
    }

    @Override
    public String generateInsight(TrendInsightContext context) {
        try {
            log.info("Generating trend insight: pair={}/{}, fromDate={}, toDate={}, dataPoints={}",
                    context.fromCurrency(), context.toCurrency(),
                    context.fromDate(), context.toDate(),
                    context.historicalRates().size());

            String userPrompt = buildUserPrompt(context);

            log.debug("User prompt constructed with {} historical values", context.historicalRates().size());

            Message systemMessage = new SystemMessage(SYSTEM_PROMPT);
            Message userMessage = new UserMessage(userPrompt);

            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

            String insight = chatClient.prompt(prompt)
                    .call()
                    .content();

            log.info("Successfully generated trend insight for {}/{}", context.fromCurrency(), context.toCurrency());

            return insight.trim();

        } catch (Exception e) {
            log.error("AI provider failed while generating insight for {}/{}: {}",
                    context.fromCurrency(), context.toCurrency(), e.getMessage());
            throw new AiProviderException(
                    "AI provider is unavailable or failed to generate insight",
                    e
            );
        }
    }

    /**
     * Build the user/context prompt with actual historical rate values and application-calculated metrics.
     * <p>
     * Format is deterministic and includes:
     * - Currency pair and date range
     * - Application-calculated first/last rates
     * - Application-calculated direction, absolute change, percentage change
     * - All actual historical raw rate points
     * <p>
     * Missing dates remain missing (not interpolated).
     * <p>
     * Package-private for testing.
     */
    String buildUserPrompt(TrendInsightContext context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Currency pair: ")
                .append(context.fromCurrency())
                .append("/")
                .append(context.toCurrency())
                .append("\n");

        prompt.append("Requested period: ")
                .append(context.fromDate())
                .append(" to ")
                .append(context.toDate())
                .append("\n\n");

        // Application-calculated metrics section
        TrendInsightContext.TrendMetrics metrics = context.metrics();
        
        prompt.append("First available rate:\n");
        prompt.append(metrics.firstDate())
                .append(": ")
                .append(formatRate(metrics.firstRate()))
                .append("\n\n");

        prompt.append("Last available rate:\n");
        prompt.append(metrics.lastDate())
                .append(": ")
                .append(formatRate(metrics.lastRate()))
                .append("\n\n");

        prompt.append("Application-calculated metrics (AUTHORITATIVE - do NOT recalculate):\n");
        prompt.append("Direction: ")
                .append(metrics.direction())
                .append("\n");
        prompt.append("Absolute change: ")
                .append(formatRate(metrics.absoluteChange()))
                .append("\n");
        
        if (metrics.percentageChange() != null) {
            prompt.append("Percentage change: ")
                    .append(formatRate(metrics.percentageChange()))
                    .append("%\n");
        } else {
            prompt.append("Percentage change: not available (first rate is zero)\n");
        }

        prompt.append("\nHistorical raw cross-rates:\n");

        DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;

        for (TrendInsightContext.HistoricalDataPoint point : context.historicalRates()) {
            prompt.append(point.date().format(dateFormatter))
                    .append(": ")
                    .append(formatRate(point.rawRate()))
                    .append("\n");
        }

        prompt.append("\nProvide a concise 2-sentence natural-language summary using the supplied direction and percentage change. Do NOT recalculate.");

        return prompt.toString();
    }

    /**
     * Format BigDecimal rate value for prompt readability.
     * <p>
     * Uses fixed scale to avoid scientific notation while preserving precision.
     * <p>
     * Package-private for testing.
     */
    String formatRate(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }
}
