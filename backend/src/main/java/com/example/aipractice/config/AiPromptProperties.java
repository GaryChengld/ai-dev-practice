package com.example.aipractice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Named system prompts available to AI-backed application workflows.
 *
 * @param chatAssistant system instruction for general chat requests
 * @param ticketAnalyzer system instruction for support-ticket analysis
 */
@ConfigurationProperties(prefix = "ai.prompts")
public record AiPromptProperties(
        String chatAssistant,
        String ticketAnalyzer
) {
}
