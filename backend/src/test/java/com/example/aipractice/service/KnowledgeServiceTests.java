package com.example.aipractice.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceTests {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final KnowledgeService knowledgeService = new KnowledgeService(vectorStore);

    @Test
    void indexesKnowledgeOnceAndReturnsTheMostRelevantChunks() {
        List<Document> results = List.of(new Document(
                "HIGH priority tickets require a response within 4 hours."
        ));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(results);

        assertThat(knowledgeService.search("What is the SLA for a HIGH ticket?"))
                .isEqualTo(results);
        knowledgeService.search("How quickly should HIGH tickets receive a response?");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        assertThat(documentsCaptor.getValue())
                .extracting(Document::getText)
                .anySatisfy(text -> assertThat(text)
                        .contains("HIGH priority tickets require a response within 4 hours."))
                .anySatisfy(text -> assertThat(text)
                        .contains("Customers may request a refund within 30 days of purchase."))
                .anySatisfy(text -> assertThat(text)
                        .contains("Passwords must contain at least 12 characters."));
        assertThat(documentsCaptor.getValue())
                .extracting(document -> document.getMetadata().get(KnowledgeMetadata.SOURCE))
                .contains(
                        "ticket-sla.md",
                        "refund-policy.md",
                        "password-policy.md"
                );
        verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void searchesForTheTopThreeRelevantChunks() {
        var requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);

        knowledgeService.search("refund timing");

        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getQuery()).isEqualTo("refund timing");
        assertThat(requestCaptor.getValue().getTopK()).isEqualTo(3);
        assertThat(requestCaptor.getValue().getSimilarityThreshold()).isEqualTo(0.7);
    }

    @Test
    void rejectsBlankQueriesBeforeLoadingKnowledge() {
        assertThatThrownBy(() -> knowledgeService.search("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Knowledge search query must not be blank");

        verify(vectorStore, times(0)).add(anyList());
    }
}
