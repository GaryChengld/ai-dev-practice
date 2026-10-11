package com.example.aipractice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Input-token budgets for AI context-management operations.
 *
 * @param queryRewriteMaxInputTokens maximum estimated input tokens for query rewriting
 * @param finalChatMaxInputTokens maximum estimated input tokens for the final chat call
 */
@ConfigurationProperties(prefix = "ai.context")
public record AiContextProperties(
        int queryRewriteMaxInputTokens,
        int finalChatMaxInputTokens
) {

    public AiContextProperties {
        if (queryRewriteMaxInputTokens <= 0 || finalChatMaxInputTokens <= 0) {
            throw new IllegalArgumentException("Token budgets must be positive");
        }
    }
}
