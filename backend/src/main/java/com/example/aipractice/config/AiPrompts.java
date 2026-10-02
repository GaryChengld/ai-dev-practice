package com.example.aipractice.config;

/**
 * Loaded prompt content used by AI-backed application workflows.
 *
 * @param chatAssistant system instruction for general chat requests
 * @param ticketAnalyzer system instruction for support-ticket analysis
 * @param knowledgeCategoryRouter system instruction for knowledge category routing
 */
public record AiPrompts(
        String chatAssistant,
        String ticketAnalyzer,
        String knowledgeCategoryRouter
) {
}
