package com.example.aipractice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Input-token budgets for AI context-management operations.
 *
 * @param queryRewriteMaxInputTokens maximum estimated input tokens for query rewriting
 */
@ConfigurationProperties(prefix = "ai.context")
public record AiContextProperties(int queryRewriteMaxInputTokens) {

    public AiContextProperties {
        if (queryRewriteMaxInputTokens <= 0) {
            throw new IllegalArgumentException(
                    "queryRewriteMaxInputTokens must be greater than zero"
            );
        }
    }
}
