package com.example.aipractice.service;

import java.util.List;
import java.util.Set;

/**
 * A labeled dataset entry for comparing RAG document-selection strategies.
 */
public record RagEvaluationCase(
        String name,
        List<RankedDocument> documents,
        int tokenBudget,
        Set<String> relevantDocumentIds
) {
}
