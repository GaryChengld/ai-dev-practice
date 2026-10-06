package com.example.aipractice.config;

/**
 * Loaded prompt content used by AI-backed application workflows.
 *
 * @param chatAssistant system instruction for general chat requests
 * @param ticketAnalyzer system instruction for support-ticket analysis
 * @param knowledgeCategoryRouter system instruction for knowledge category routing
 * @param knowledgeQueryRewriter system instruction for knowledge query rewriting
 * @param conversationSummarizer system instruction for rolling conversation summaries
 * @param chatSummary template that adds a conversation summary to the chat system prompt
 */
public record AiPrompts(
        String chatAssistant,
        String ticketAnalyzer,
        String knowledgeCategoryRouter,
        String knowledgeQueryRewriter,
        String conversationSummarizer,
        String chatSummary
) {
}
