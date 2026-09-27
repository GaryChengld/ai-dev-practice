package com.example.aipractice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Names of Markdown resources containing prompts for AI workflows.
 *
 * @param chatAssistant Markdown filename for the general chat prompt
 * @param ticketAnalyzer Markdown filename for the ticket-analysis prompt
 */
@ConfigurationProperties(prefix = "ai.prompts")
public record AiPromptProperties(
        String chatAssistant,
        String ticketAnalyzer
) {
}
