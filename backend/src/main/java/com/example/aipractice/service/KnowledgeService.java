package com.example.aipractice.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Provides application knowledge that can be retrieved for AI responses.
 */
@Service
public class KnowledgeService {

    private static final String KNOWLEDGE_RESOURCE_PATTERN = "classpath*:knowledge/*.md";
    private static final int TOP_K = 3;

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();
    private final TokenTextSplitter textSplitter = TokenTextSplitter.builder()
            .withChunkSize(200)
            .withMinChunkSizeChars(20)
            .withMinChunkLengthToEmbed(5)
            .build();
    private volatile boolean knowledgeLoaded;

    /**
     * Creates a knowledge service backed by a vector store.
     *
     * @param vectorStore vector store used to index and retrieve knowledge chunks
     */
    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Finds the knowledge chunks most relevant to a question.
     *
     * @param query question or search phrase
     * @return up to three relevant knowledge chunks
     */
    public List<Document> search(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Knowledge search query must not be blank");
        }

        loadKnowledgeOnce();
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(TOP_K)
                .build());
    }

    private void loadKnowledgeOnce() {
        if (!knowledgeLoaded) {
            synchronized (this) {
                if (!knowledgeLoaded) {
                    vectorStore.add(textSplitter.apply(loadKnowledgeDocuments()));
                    knowledgeLoaded = true;
                }
            }
        }
    }

    private List<Document> loadKnowledgeDocuments() {
        List<Document> documents = new ArrayList<>();
        try {
            Resource[] resources = resourceResolver.getResources(KNOWLEDGE_RESOURCE_PATTERN);
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename));

            for (Resource resource : resources) {
                documents.add(new Document(
                        resource.getContentAsString(StandardCharsets.UTF_8),
                        Map.of(KnowledgeMetadata.SOURCE, resource.getFilename())
                ));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load knowledge resources from: " + KNOWLEDGE_RESOURCE_PATTERN,
                    exception
            );
        }
        return documents;
    }
}
