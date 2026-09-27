package com.example.aipractice.config;

/**
 * Loaded prompt content used by AI-backed application workflows.
 *
 * @param chatAssistant system instruction for general chat requests
 * @param ticketAnalyzer system instruction for support-ticket analysis
 */
public record AiPrompts(
        String chatAssistant,
        String ticketAnalyzer
) {
}
