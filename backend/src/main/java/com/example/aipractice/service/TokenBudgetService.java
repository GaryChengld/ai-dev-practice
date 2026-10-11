package com.example.aipractice.service;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Estimates prompt token usage for application-level input budgets.
 */
@Service
public class TokenBudgetService {

    private static final int MESSAGE_OVERHEAD_TOKENS = 8;
    private static final int TOOL_DEFINITION_OVERHEAD_TOKENS = 8;

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
     * Estimates a complete prompt, including system and user message overhead.
     * Tool schemas and provider-specific formatting are not included.
     *
     * @param systemPrompt rendered system prompt
     * @param history conversation messages between the system and user messages
     * @param userMessage current user message, including any retrieved knowledge
     * @return estimated input token count
     */
    public int estimatePromptTokens(
            String systemPrompt,
            List<Message> history,
            String userMessage
    ) {
        return estimatePromptTokens(systemPrompt, history, userMessage, List.of());
    }

    /**
     * Estimates a complete prompt, including registered tool definitions.
     * Provider-specific formatting around those definitions is not included.
     *
     * @param systemPrompt rendered system prompt
     * @param history conversation messages between the system and user messages
     * @param userMessage current user message, including any retrieved knowledge
     * @param toolCallbacks tools exposed to the model
     * @return estimated input token count
     */
    public int estimatePromptTokens(
            String systemPrompt,
            List<Message> history,
            String userMessage,
            List<ToolCallback> toolCallbacks
    ) {
        return estimateTokens(systemPrompt)
                + MESSAGE_OVERHEAD_TOKENS
                + estimateTokens(history)
                + estimateTokens(userMessage)
                + MESSAGE_OVERHEAD_TOKENS
                + estimateToolTokens(toolCallbacks);
    }

    /**
     * Estimates tool names, descriptions, JSON input schemas, and definition overhead.
     *
     * @param toolCallbacks tools exposed to the model
     * @return estimated tool-definition token count
     */
    public int estimateToolTokens(List<ToolCallback> toolCallbacks) {
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            return 0;
        }
        return toolCallbacks.stream()
                .map(ToolCallback::getToolDefinition)
                .mapToInt(definition -> estimateTokens(definition.name())
                        + estimateTokens(definition.description())
                        + estimateTokens(definition.inputSchema())
                        + TOOL_DEFINITION_OVERHEAD_TOKENS)
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
