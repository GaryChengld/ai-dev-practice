package com.example.aipractice.service;

import com.example.aipractice.config.AiPrompts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicKnowledgeRoutingTests {

    @Test
    void discoversNewCategoryAndUsesItForRoutingAndFiltering() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("{\"category\":\"shipping\"}"))
        )));
        KnowledgeCategoryRouter router = new KnowledgeCategoryRouter(
                ChatClient.create(chatModel),
                new AiPrompts(
                        "Assist the user.",
                        "Analyze the ticket.",
                        """
                                You route the user's question to one available knowledge category.

                                Available categories:
                                {{categories}}

                                Choose exactly one category from the list above.
                                If none applies, return "none".
                                """,
                        "Rewrite knowledge questions."
                )
        );

        Resource shippingResource = mock(Resource.class);
        when(shippingResource.getFilename()).thenReturn("shipping-policy.md");
        when(shippingResource.getContentAsString(any())).thenReturn("""
                ---
                category: shipping
                ---
                # Shipping Policy

                Standard shipping normally takes 3–5 business days.
                """);
        ResourcePatternResolver resourceResolver = mock(ResourcePatternResolver.class);
        when(resourceResolver.getResources(anyString()))
                .thenReturn(new Resource[]{shippingResource});

        Document shippingResult = new Document(
                "Standard shipping normally takes 3–5 business days.",
                Map.of(
                        KnowledgeMetadata.SOURCE, "shipping-policy.md",
                        KnowledgeMetadata.CATEGORY, "shipping"
                )
        );
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(shippingResult));
        KnowledgeService knowledgeService = new KnowledgeService(
                vectorStore,
                router,
                resourceResolver
        );

        List<Document> results = knowledgeService.search("When will my package arrive?");

        assertThat(results)
                .extracting(document -> document.getMetadata().get(KnowledgeMetadata.SOURCE))
                .containsExactly("shipping-policy.md");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .filteredOn(SystemMessage.class::isInstance)
                .extracting(instruction -> instruction.getText())
                .singleElement()
                .satisfies(prompt -> assertThat(prompt)
                        .contains("Available categories:\n- shipping")
                        .doesNotContain("{{categories}}"));

        ArgumentCaptor<SearchRequest> requestCaptor =
                ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFilterExpression()).isEqualTo(
                new FilterExpressionBuilder()
                        .eq(KnowledgeMetadata.CATEGORY, "shipping")
                        .build()
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> indexedDocumentsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(indexedDocumentsCaptor.capture());
        assertThat(indexedDocumentsCaptor.getValue())
                .anySatisfy(document -> assertThat(document.getMetadata())
                        .containsEntry(KnowledgeMetadata.SOURCE, "shipping-policy.md")
                        .containsEntry(KnowledgeMetadata.CATEGORY, "shipping"));
    }
}
