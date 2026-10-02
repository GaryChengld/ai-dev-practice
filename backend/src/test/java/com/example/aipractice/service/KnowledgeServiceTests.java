package com.example.aipractice.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceTests {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final KnowledgeCategoryRouter knowledgeCategoryRouter =
            mock(KnowledgeCategoryRouter.class);
    private final KnowledgeService knowledgeService = new KnowledgeService(
            vectorStore,
            knowledgeCategoryRouter
    );

    KnowledgeServiceTests() {
        when(knowledgeCategoryRouter.route(anyString()))
                .thenReturn(new RoutingDecision(KnowledgeCategoryRouter.NO_CATEGORY));
    }

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
        assertThat(documentsCaptor.getValue())
                .anySatisfy(document -> assertThat(document.getMetadata())
                        .containsEntry(KnowledgeMetadata.SOURCE, "ticket-sla.md")
                        .containsEntry(KnowledgeMetadata.CATEGORY, "ticket"))
                .anySatisfy(document -> assertThat(document.getMetadata())
                        .containsEntry(KnowledgeMetadata.SOURCE, "refund-policy.md")
                        .containsEntry(KnowledgeMetadata.CATEGORY, "refund"))
                .anySatisfy(document -> assertThat(document.getMetadata())
                        .containsEntry(KnowledgeMetadata.SOURCE, "password-policy.md")
                        .containsEntry(KnowledgeMetadata.CATEGORY, "security"));
        assertThat(documentsCaptor.getValue())
                .extracting(Document::getText)
                .allSatisfy(text -> assertThat(text)
                        .doesNotContain("category:", "---"));
        verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void indexesKnowledgeWithoutOptionalCategoryMetadata() throws IOException {
        ResourcePatternResolver resourceResolver = mock(ResourcePatternResolver.class);
        Resource resource = mock(Resource.class);
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{resource});
        when(resource.getFilename()).thenReturn("general-info.md");
        when(resource.getContentAsString(any())).thenReturn("""
                ---
                audiences:
                  - customer
                  - employee
                ---
                # General Information

                General company information.
                """);
        KnowledgeService uncategorizedKnowledgeService =
                new KnowledgeService(
                        vectorStore,
                        knowledgeCategoryRouter,
                        resourceResolver
                );

        uncategorizedKnowledgeService.search("company information");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        assertThat(documentsCaptor.getValue())
                .allSatisfy(document -> assertThat(document.getMetadata())
                        .containsEntry(KnowledgeMetadata.SOURCE, "general-info.md")
                        .containsEntry("audiences", List.of("customer", "employee"))
                        .doesNotContainKey(KnowledgeMetadata.CATEGORY));
    }

    @Test
    void searchesForTheTopThreeRelevantChunks() {
        var requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);

        knowledgeService.search("refund timing");

        verify(knowledgeCategoryRouter).route("refund timing");
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getQuery()).isEqualTo("refund timing");
        assertThat(requestCaptor.getValue().getTopK()).isEqualTo(3);
        assertThat(requestCaptor.getValue().getSimilarityThreshold()).isEqualTo(0.7);
        assertThat(requestCaptor.getValue().hasFilterExpression()).isFalse();
    }

    @Test
    void filtersSearchWhenRouterSelectsACategory() {
        var requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        when(knowledgeCategoryRouter.route("How long do I have to request a refund?"))
                .thenReturn(new RoutingDecision("refund"));

        knowledgeService.search("How long do I have to request a refund?");

        verify(knowledgeCategoryRouter).route("How long do I have to request a refund?");
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFilterExpression()).isEqualTo(
                new FilterExpressionBuilder()
                        .eq(KnowledgeMetadata.CATEGORY, "refund")
                        .build()
        );
    }

    @Test
    void rejectsBlankQueriesBeforeLoadingKnowledge() {
        assertThatThrownBy(() -> knowledgeService.search("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Knowledge search query must not be blank");

        verify(vectorStore, times(0)).add(anyList());
        verify(knowledgeCategoryRouter, times(0)).route(anyString());
    }
}
