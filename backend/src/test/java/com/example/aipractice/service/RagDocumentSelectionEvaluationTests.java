package com.example.aipractice.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.data.Offset.offset;

class RagDocumentSelectionEvaluationTests {

    private final TokenBudgetService tokenBudgetService = new TokenBudgetService();
    private final RagContextBudgetService ragContextBudgetService =
            new RagContextBudgetService(tokenBudgetService);

    @Test
    void comparesRetrievalOrderAndValueDensityAgainstLabeledCases() {
        RagEvaluationCase valueDensityWins = new RagEvaluationCase(
                "Ticket escalation policy",
                List.of(
                        rankedDocument("A", 1_000, 0.95, 0),
                        rankedDocument("B", 600, 0.90, 1),
                        rankedDocument("C", 500, 0.85, 2)
                ),
                1_100,
                Set.of("B", "C")
        );
        RagEvaluationCase retrievalOrderWins = new RagEvaluationCase(
                "Specialized troubleshooting",
                List.of(
                        rankedDocument("A", 900, 0.95, 0),
                        rankedDocument("B", 600, 0.90, 1),
                        rankedDocument("C", 500, 0.85, 2),
                        rankedDocument("D", 400, 0.70, 3)
                ),
                1_000,
                Set.of("A")
        );

        StrategyComparison first = evaluate(valueDensityWins);
        StrategyComparison second = evaluate(retrievalOrderWins);

        assertMetrics(first.retrievalOrder(), List.of("A"), 0.0, 1_000, 10.0 / 11.0);
        assertMetrics(first.valueDensity(), List.of("B", "C"), 1.0, 1_100, 1.0);
        assertMetrics(second.retrievalOrder(), List.of("A"), 1.0, 900, 0.9);
        assertMetrics(second.valueDensity(), List.of("C", "D"), 0.0, 900, 0.9);

        report(first);
        report(second);
    }

    @Test
    void rejectsEvaluationCasesWithoutRelevantDocumentLabels() {
        RagEvaluationCase testCase = new RagEvaluationCase(
                "Missing labels",
                List.of(rankedDocument("A", 100, 0.95, 0)),
                100,
                Set.of()
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> evaluate(testCase))
                .withMessage("Evaluation case must contain relevant documents");
    }

    @Test
    void requiresAPositiveEvaluationBudget() {
        RagEvaluationCase testCase = new RagEvaluationCase(
                "Invalid budget",
                List.of(rankedDocument("A", 100, 0.95, 0)),
                0,
                Set.of("A")
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> evaluate(testCase))
                .withMessage("Evaluation token budget must be positive");
    }

    private StrategyComparison evaluate(RagEvaluationCase testCase) {
        if (testCase.tokenBudget() <= 0) {
            throw new IllegalArgumentException("Evaluation token budget must be positive");
        }

        List<Document> retrievalOrder = selectByRetrievalOrder(
                testCase.documents(),
                testCase.tokenBudget()
        );
        List<Document> valueDensity = ragContextBudgetService.selectByValueDensity(
                testCase.documents(),
                testCase.tokenBudget()
        );

        return new StrategyComparison(
                testCase.name(),
                testCase.tokenBudget(),
                metrics(retrievalOrder, testCase),
                metrics(valueDensity, testCase)
        );
    }

    private List<Document> selectByRetrievalOrder(
            List<RankedDocument> documents,
            int budget
    ) {
        List<Document> selected = new ArrayList<>();
        int usedTokens = 0;

        for (RankedDocument ranked : documents) {
            int tokens = tokenBudgetService.estimateTokens(
                    ranked.document().getText()
            );
            if (usedTokens + tokens <= budget) {
                selected.add(ranked.document());
                usedTokens += tokens;
            }
        }

        return List.copyOf(selected);
    }

    private EvaluationMetrics metrics(
            List<Document> selected,
            RagEvaluationCase testCase
    ) {
        List<String> selectedIds = selected.stream()
                .map(this::documentId)
                .toList();
        int usedTokens = selected.stream()
                .mapToInt(document -> tokenBudgetService.estimateTokens(
                        document.getText()
                ))
                .sum();

        return new EvaluationMetrics(
                selectedIds,
                calculateRecall(Set.copyOf(selectedIds), testCase.relevantDocumentIds()),
                calculateTokenUtilization(selected, testCase.tokenBudget()),
                usedTokens,
                selected.size()
        );
    }

    private double calculateRecall(
            Set<String> selectedIds,
            Set<String> relevantIds
    ) {
        if (relevantIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Evaluation case must contain relevant documents"
            );
        }

        long matches = selectedIds.stream()
                .filter(relevantIds::contains)
                .count();
        return (double) matches / relevantIds.size();
    }

    private double calculateTokenUtilization(
            List<Document> selected,
            int budget
    ) {
        if (budget <= 0) {
            throw new IllegalArgumentException(
                    "Evaluation token budget must be positive"
            );
        }

        int usedTokens = selected.stream()
                .mapToInt(document -> tokenBudgetService.estimateTokens(
                        document.getText()
                ))
                .sum();
        return (double) usedTokens / budget;
    }

    private String documentId(Document document) {
        return document.getMetadata()
                .get(KnowledgeMetadata.SOURCE)
                .toString();
    }

    private RankedDocument rankedDocument(
            String id,
            int tokens,
            double relevance,
            int originalIndex
    ) {
        Document document = new Document(
                "x".repeat(tokens * 3),
                Map.of(KnowledgeMetadata.SOURCE, id)
        );
        return new RankedDocument(document, relevance, originalIndex);
    }

    private void assertMetrics(
            EvaluationMetrics actual,
            List<String> selectedIds,
            double recall,
            int usedTokens,
            double utilization
    ) {
        assertThat(actual.selectedIds()).containsExactlyElementsOf(selectedIds);
        assertThat(actual.recall()).isEqualTo(recall);
        assertThat(actual.usedTokens()).isEqualTo(usedTokens);
        assertThat(actual.tokenUtilization()).isCloseTo(
                utilization,
                offset(0.000_001)
        );
        assertThat(actual.selectedDocumentCount()).isEqualTo(selectedIds.size());
    }

    private void report(StrategyComparison comparison) {
        System.out.println(
                """
                Case: %s

                Budget: %d tokens

                Retrieval Order:
                  %s

                Value Density:
                  %s
                """.formatted(
                        comparison.name(),
                        comparison.tokenBudget(),
                        format(comparison.retrievalOrder()),
                        format(comparison.valueDensity())
                )
        );
    }

    private String format(EvaluationMetrics metrics) {
        return ("Selected: %s%n  Recall: %.0f%%%n  Tokens used: %d%n"
                + "  Token utilization: %.1f%%%n  Document count: %d")
                .formatted(
                        String.join(", ", metrics.selectedIds()),
                        metrics.recall() * 100,
                        metrics.usedTokens(),
                        metrics.tokenUtilization() * 100,
                        metrics.selectedDocumentCount()
                );
    }

    private record EvaluationMetrics(
            List<String> selectedIds,
            double recall,
            double tokenUtilization,
            int usedTokens,
            int selectedDocumentCount
    ) {
    }

    private record StrategyComparison(
            String name,
            int tokenBudget,
            EvaluationMetrics retrievalOrder,
            EvaluationMetrics valueDensity
    ) {
    }
}
