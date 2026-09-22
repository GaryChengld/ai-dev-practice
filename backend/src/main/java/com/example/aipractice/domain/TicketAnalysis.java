package com.example.aipractice.domain;

/**
 * Structured result produced when the AI model analyzes a support ticket.
 *
 * @param category ticket classification
 * @param priority ticket urgency
 * @param summary concise summary of the reported problem
 * @param suggestedAction practical next action for resolving the problem
 */
public record TicketAnalysis(
        Category category,
        Priority priority,
        String summary,
        String suggestedAction
) {
}
