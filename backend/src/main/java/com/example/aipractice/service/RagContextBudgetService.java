package com.example.aipractice.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Selects retrieved knowledge that fits the token budget left after essential context.
 */
@Service
public class RagContextBudgetService {

    private final TokenBudgetService tokenBudgetService;

    public RagContextBudgetService(TokenBudgetService tokenBudgetService) {
        this.tokenBudgetService = tokenBudgetService;
    }

    /**
     * Selects documents by estimated relevance per token, then restores retrieval order.
     *
     * @param documents retrieved documents with relevance and original position
     * @param availableTokens token budget available for document text
     * @return selected documents in their original retrieval order
     */
    public List<Document> selectByValueDensity(
            List<RankedDocument> documents,
            int availableTokens
    ) {
        List<RankedDocument> prioritized = documents.stream()
                .sorted(Comparator.comparingDouble(
                        this::valueDensity
                ).reversed())
                .toList();

        List<RankedDocument> selected = new ArrayList<>();
        int usedTokens = 0;

        for (RankedDocument ranked : prioritized) {
            int documentTokens = tokenBudgetService.estimateTokens(
                    ranked.document().getText()
            );
            if (usedTokens + documentTokens <= availableTokens) {
                selected.add(ranked);
                usedTokens += documentTokens;
            }
        }

        return selected.stream()
                .sorted(Comparator.comparingInt(RankedDocument::originalIndex))
                .map(RankedDocument::document)
                .toList();
    }

    private double valueDensity(RankedDocument ranked) {
        int estimatedTokens = tokenBudgetService.estimateTokens(
                ranked.document().getText()
        );
        return ranked.relevance() / Math.max(1, estimatedTokens);
    }
}
