package com.example.aipractice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Names of Markdown resources containing prompts for AI workflows.
 *
 * @param chatAssistant Markdown filename for the general chat prompt
 * @param ticketAnalyzer Markdown filename for the ticket-analysis prompt
 * @param knowledgeCategoryRouter Markdown filename for the knowledge-category router prompt
 * @param knowledgeQueryRewriter Markdown filename for the knowledge-query rewriter prompt
 * @param conversationSummarizer Markdown filename for the conversation summarizer prompt
 * @param chatSummary Markdown filename for the chat summary-context template
 */
@ConfigurationProperties(prefix = "ai.prompts")
public record AiPromptProperties(
        String chatAssistant,
        String ticketAnalyzer,
        String knowledgeCategoryRouter,
        String knowledgeQueryRewriter,
        String conversationSummarizer,
        String chatSummary
) {
}
