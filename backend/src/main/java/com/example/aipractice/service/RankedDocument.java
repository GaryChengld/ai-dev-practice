package com.example.aipractice.service;

import org.springframework.ai.document.Document;

/**
 * Associates a retrieved document with its relevance and original retrieval position.
 */
public record RankedDocument(
        Document document,
        double relevance,
        int originalIndex
) {
}
