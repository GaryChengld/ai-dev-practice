package com.example.aipractice.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RagAnswerEvaluationTests {

    @Test
    void evaluatesCorrectnessFaithfulnessAndRelevance() {
        RagAnswerCase correctAnswer = new RagAnswerCase(
                "When should a ticket be escalated?",
                "Escalate after two hours.",
                Set.of("ESCALATE_AFTER_2H"),
                Set.of("ESCALATE_AFTER_2H"),
                Set.of("ESCALATE_AFTER_2H"),
                Set.of("ESCALATE_AFTER_2H")
        );
        RagAnswerCase unsupportedClaim = new RagAnswerCase(
                "When should a ticket be escalated?",
                "Escalate after two hours and notify the CEO.",
                Set.of("ESCALATE_AFTER_2H"),
                Set.of("ESCALATE_AFTER_2H"),
                Set.of(
                        "ESCALATE_AFTER_2H",
                        "NOTIFY_CEO"
                ),
                Set.of(
                        "ESCALATE_AFTER_2H",
                        "NOTIFY_CEO"
                )
        );
        RagAnswerCase irrelevantAnswer = new RagAnswerCase(
                "When should a ticket be escalated?",
                "Contact IT to reset your password.",
                Set.of("ESCALATE_AFTER_2H"),
                Set.of(
                        "ESCALATE_AFTER_2H",
                        "RESET_PASSWORD"
                ),
                Set.of("RESET_PASSWORD"),
                Set.of("ESCALATE_AFTER_2H")
        );

        AnswerMetrics correctResult = evaluate(correctAnswer);
        AnswerMetrics unsupportedResult = evaluate(unsupportedClaim);
        AnswerMetrics irrelevantResult = evaluate(irrelevantAnswer);

        assertThat(correctResult.correctness()).isEqualTo(1.0);
        assertThat(correctResult.faithfulness()).isEqualTo(1.0);
        assertThat(correctResult.relevance()).isEqualTo(1.0);

        assertThat(unsupportedResult.correctness()).isEqualTo(1.0);
        assertThat(unsupportedResult.faithfulness()).isEqualTo(0.5);
        assertThat(unsupportedResult.relevance()).isEqualTo(1.0);

        assertThat(irrelevantResult.correctness()).isEqualTo(0.0);
        assertThat(irrelevantResult.faithfulness()).isEqualTo(1.0);
        assertThat(irrelevantResult.relevance()).isEqualTo(0.0);
    }

    private AnswerMetrics evaluate(RagAnswerCase testCase) {
        if (testCase.expectedFacts().isEmpty()) {
            throw new IllegalArgumentException(
                    "Expected facts cannot be empty"
            );
        }

        if (testCase.answerFacts().isEmpty()) {
            throw new IllegalArgumentException(
                    "Answer claims cannot be empty"
            );
        }

        long correctFacts = testCase.answerFacts().stream()
                .filter(testCase.expectedFacts()::contains)
                .count();

        long supportedClaims = testCase.answerFacts().stream()
                .filter(testCase.retrievedFacts()::contains)
                .count();

        long relevantClaims = testCase.answerFacts().stream()
                .filter(testCase.onTopicFacts()::contains)
                .count();

        return new AnswerMetrics(
                (double) correctFacts
                        / testCase.expectedFacts().size(),

                (double) supportedClaims
                        / testCase.answerFacts().size(),

                (double) relevantClaims
                        / testCase.answerFacts().size()
        );
    }

    private record RagAnswerCase(
            String question,
            String generatedAnswer,
            Set<String> expectedFacts,
            Set<String> retrievedFacts,
            Set<String> answerFacts,
            Set<String> onTopicFacts
    ) {
    }

    private record AnswerMetrics(
            double correctness,
            double faithfulness,
            double relevance
    ) {
    }
}
