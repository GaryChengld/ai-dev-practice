package com.example.aipractice.service;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Estimates prompt token usage for application-level input budgets.
 */
@Service
public class TokenBudgetService {

    private static final int MESSAGE_OVERHEAD_TOKENS = 8;

    /**
     * Estimates tokens using the exercise's three-characters-per-token heuristic.
     *
     * @param text text to estimate
     * @return estimated token count
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 3.0);
    }

    /**
     * Estimates message content plus per-message role and structure overhead.
     *
     * @param messages messages to estimate
     * @return estimated token count
     */
    public int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream()
                .mapToInt(message -> estimateTokens(message.getText())
                        + MESSAGE_OVERHEAD_TOKENS)
                .sum();
    }

    /**
     * Tests an estimate against an inclusive input budget.
     *
     * @param estimatedTokens estimated input size
     * @param maxInputTokens configured maximum input size
     * @return {@code true} when the estimate is within budget
     */
    public boolean fitsBudget(int estimatedTokens, int maxInputTokens) {
        return estimatedTokens <= maxInputTokens;
    }
}
