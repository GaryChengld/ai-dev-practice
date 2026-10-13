package com.example.aipractice.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagContextBudgetServiceTests {

    private final TokenBudgetService tokenBudgetService = mock(TokenBudgetService.class);
    private final RagContextBudgetService service =
            new RagContextBudgetService(tokenBudgetService);

    @Test
    void selectsAllDocumentsWhenTheyFit() {
        Document documentA = new Document("Document A");
        Document documentB = new Document("Document B");
        Document documentC = new Document("Document C");
        when(tokenBudgetService.estimateTokens("Document A")).thenReturn(1_000);
        when(tokenBudgetService.estimateTokens("Document B")).thenReturn(1_000);
        when(tokenBudgetService.estimateTokens("Document C")).thenReturn(500);

        List<Document> selected = service.selectByValueDensity(
                List.of(
                        new RankedDocument(documentA, 0.95, 0),
                        new RankedDocument(documentB, 0.90, 1),
                        new RankedDocument(documentC, 0.85, 2)
                ),
                3_000
        );

        assertThat(selected).containsExactly(documentA, documentB, documentC);
    }

    @Test
    void selectsHigherValueDensityAndReturnsDocumentsInRetrievalOrder() {
        Document documentA = new Document("Document A");
        Document documentB = new Document("Document B");
        Document documentC = new Document("Document C");
        when(tokenBudgetService.estimateTokens("Document A")).thenReturn(1_000);
        when(tokenBudgetService.estimateTokens("Document B")).thenReturn(600);
        when(tokenBudgetService.estimateTokens("Document C")).thenReturn(500);

        List<Document> selected = service.selectByValueDensity(
                List.of(
                        new RankedDocument(documentA, 0.95, 0),
                        new RankedDocument(documentB, 0.90, 1),
                        new RankedDocument(documentC, 0.85, 2)
                ),
                1_200
        );

        assertThat(selected).containsExactly(documentB, documentC);
    }
}
