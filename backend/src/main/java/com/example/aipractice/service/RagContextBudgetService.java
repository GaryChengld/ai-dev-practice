package com.example.aipractice.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
     * Greedily selects documents in retrieval order, skipping documents that do not fit.
     *
     * @param documents documents ordered from most to least relevant
     * @param availableTokens token budget available for document text
     * @return an immutable list of selected documents in their original order
     */
    public List<Document> selectDocuments(
            List<Document> documents,
            int availableTokens
    ) {
        List<Document> selected = new ArrayList<>();
        int usedTokens = 0;

        for (Document document : documents) {
            int documentTokens = tokenBudgetService.estimateTokens(document.getText());
            if (usedTokens + documentTokens <= availableTokens) {
                selected.add(document);
                usedTokens += documentTokens;
            }
        }

        return List.copyOf(selected);
    }
}
