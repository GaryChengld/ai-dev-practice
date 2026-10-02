package com.example.aipractice.service;

/**
 * Structured result produced by the knowledge category router.
 *
 * @param category selected knowledge category
 */
public record RoutingDecision(
        String category
) {
}
