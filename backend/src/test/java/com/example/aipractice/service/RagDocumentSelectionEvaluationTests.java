package com.example.aipractice.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.data.Offset.offset;

class RagDocumentSelectionEvaluationTests {

    private final TokenBudgetService tokenBudgetService = new TokenBudgetService();
    private final RagContextBudgetService ragContextBudgetService =
            new RagContextBudgetService(tokenBudgetService);

    @Test
    void comparesRetrievalOrderRankDensityAndSimilarityDensityAgainstLabeledCases() {
        RagEvaluationCase similarityDensityWins = new RagEvaluationCase(
                "Ticket escalation policy",
                List.of(
                        rankedDocument("A", 900, 0.95, 0),
                        rankedDocument("B", 600, 0.90, 1),
                        rankedDocument("C", 500, 0.85, 2)
                ),
                1_100,
                Set.of("B", "C")
        );
        RagEvaluationCase retrievalOrderAndRankDensityWin = new RagEvaluationCase(
                "Specialized troubleshooting",
                List.of(
                        rankedDocument("A", 850, 0.99, 0),
                        rankedDocument("B", 550, 0.96, 1),
                        rankedDocument("C", 450, 0.94, 2)
                ),
                1_000,
                Set.of("A")
        );

        StrategyComparison first = evaluate(similarityDensityWins);
        StrategyComparison second = evaluate(retrievalOrderAndRankDensityWin);

        assertMetrics(first.retrievalOrder(), List.of("A"), 0.0, 900, 9.0 / 11.0);
        assertMetrics(first.rankDensity(), List.of("A"), 0.0, 900, 9.0 / 11.0);
        assertMetrics(first.similarityDensity(), List.of("B", "C"), 1.0, 1_100, 1.0);
        assertMetrics(second.retrievalOrder(), List.of("A"), 1.0, 850, 0.85);
        assertMetrics(second.rankDensity(), List.of("A"), 1.0, 850, 0.85);
        assertMetrics(second.similarityDensity(), List.of("B", "C"), 0.0, 1_000, 1.0);

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
        List<Document> rankDensity = ragContextBudgetService.selectByValueDensity(
                withRankScores(testCase.documents()),
                testCase.tokenBudget()
        );
        List<Document> similarityDensity = ragContextBudgetService.selectByValueDensity(
                withSimilarityScores(testCase.documents()),
                testCase.tokenBudget()
        );

        return new StrategyComparison(
                testCase.name(),
                testCase.tokenBudget(),
                metrics(retrievalOrder, testCase),
                metrics(rankDensity, testCase),
                metrics(similarityDensity, testCase)
        );
    }

    private List<RankedDocument> withRankScores(
            List<RankedDocument> documents
    ) {
        return IntStream.range(0, documents.size())
                .mapToObj(index -> new RankedDocument(
                        documents.get(index).document(),
                        1.0 / (index + 1),
                        index
                ))
                .toList();
    }

    private List<RankedDocument> withSimilarityScores(
            List<RankedDocument> documents
    ) {
        return documents.stream()
                .map(ranked -> {
                    Double score = ranked.document().getScore();
                    if (score == null
                            || !Double.isFinite(score)
                            || score < 0.0
                            || score > 1.0) {
                        throw new IllegalArgumentException(
                                "Evaluation requires valid similarity scores"
                        );
                    }

                    return new RankedDocument(
                            ranked.document(),
                            score,
                            ranked.originalIndex()
                    );
                })
                .toList();
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
        Document document = Document.builder()
                .text("x".repeat(tokens * 3))
                .metadata(KnowledgeMetadata.SOURCE, id)
                .score(relevance)
                .build();
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

                Rank Density:
                  %s

                Similarity Density:
                  %s
                """.formatted(
                        comparison.name(),
                        comparison.tokenBudget(),
                        format(comparison.retrievalOrder()),
                        format(comparison.rankDensity()),
                        format(comparison.similarityDensity())
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
            EvaluationMetrics rankDensity,
            EvaluationMetrics similarityDensity
    ) {
    }
}
