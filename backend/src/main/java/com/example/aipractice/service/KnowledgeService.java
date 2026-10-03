package com.example.aipractice.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Provides application knowledge that can be retrieved for AI responses.
 */
@Service
public class KnowledgeService {

    private static final String KNOWLEDGE_RESOURCE_PATTERN = "classpath*:knowledge/*.md";
    private static final String FRONT_MATTER_DELIMITER = "---";
    private static final int TOP_K = 3;
    private static final double SIMILARITY_THRESHOLD = 0.7;

    private final VectorStore vectorStore;
    private final KnowledgeCategoryRouter knowledgeCategoryRouter;
    private final ResourcePatternResolver resourceResolver;
    private final Yaml yaml = createYamlParser();
    private final TokenTextSplitter textSplitter = TokenTextSplitter.builder()
            .withChunkSize(200)
            .withMinChunkSizeChars(20)
            .withMinChunkLengthToEmbed(5)
            .build();
    private volatile Set<String> availableCategories = Set.of();
    private volatile boolean knowledgeLoaded;

    /**
     * Creates a knowledge service backed by a vector store.
     *
     * @param vectorStore vector store used to index and retrieve knowledge chunks
     * @param knowledgeCategoryRouter router that selects an optional knowledge category
     */
    @Autowired
    public KnowledgeService(
            VectorStore vectorStore,
            KnowledgeCategoryRouter knowledgeCategoryRouter
    ) {
        this(
                vectorStore,
                knowledgeCategoryRouter,
                new PathMatchingResourcePatternResolver()
        );
    }

    KnowledgeService(
            VectorStore vectorStore,
            KnowledgeCategoryRouter knowledgeCategoryRouter,
            ResourcePatternResolver resourceResolver
    ) {
        this.vectorStore = vectorStore;
        this.knowledgeCategoryRouter = knowledgeCategoryRouter;
        this.resourceResolver = resourceResolver;
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
        RoutingDecision routingDecision = knowledgeCategoryRouter.route(
                query,
                availableCategories
        );
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(query)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD);

        if (!KnowledgeCategoryRouter.NO_CATEGORY.equals(routingDecision.category())) {
            requestBuilder.filterExpression(new FilterExpressionBuilder()
                    .eq(KnowledgeMetadata.CATEGORY, routingDecision.category())
                    .build());
        }

        return vectorStore.similaritySearch(requestBuilder.build());
    }

    private void loadKnowledgeOnce() {
        if (!knowledgeLoaded) {
            synchronized (this) {
                if (!knowledgeLoaded) {
                    List<Document> documents = loadKnowledgeDocuments();
                    Set<String> discoveredCategories = discoverCategories(documents);
                    vectorStore.add(textSplitter.apply(documents));
                    availableCategories = discoveredCategories;
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
                documents.add(parseKnowledgeFile(resource));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load knowledge resources from: " + KNOWLEDGE_RESOURCE_PATTERN,
                    exception
            );
        }
        return documents;
    }

    private Set<String> discoverCategories(List<Document> documents) {
        Set<String> categories = new TreeSet<>();
        documents.stream()
                .map(document -> document.getMetadata().get(KnowledgeMetadata.CATEGORY))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(category -> !category.isBlank())
                .forEach(categories::add);
        return Set.copyOf(categories);
    }

    private Document parseKnowledgeFile(Resource resource) throws IOException {
        String source = resource.getFilename();
        String content = resource.getContentAsString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        String openingDelimiter = FRONT_MATTER_DELIMITER + "\n";
        String closingDelimiter = "\n" + FRONT_MATTER_DELIMITER + "\n";
        if (!content.startsWith(openingDelimiter)) {
            throw new IllegalStateException("Knowledge file is missing metadata: " + source);
        }

        int metadataEnd = content.indexOf(closingDelimiter, openingDelimiter.length());
        if (metadataEnd < 0) {
            throw new IllegalStateException("Knowledge file has invalid metadata: " + source);
        }

        Map<String, Object> metadata = parseMetadata(
                content.substring(openingDelimiter.length(), metadataEnd),
                source
        );
        validateOptionalCategory(metadata, source);
        metadata.put(KnowledgeMetadata.SOURCE, source);

        String documentContent = content.substring(metadataEnd + closingDelimiter.length())
                .stripLeading();
        return new Document(documentContent, Map.copyOf(metadata));
    }

    private Map<String, Object> parseMetadata(String metadataBlock, String source) {
        Object parsedMetadata;
        try {
            parsedMetadata = yaml.load(metadataBlock);
        } catch (YAMLException exception) {
            throw new IllegalStateException(
                    "Invalid YAML metadata in knowledge file: " + source,
                    exception
            );
        }

        if (!(parsedMetadata instanceof Map<?, ?> yamlMetadata)) {
            throw new IllegalStateException(
                    "Knowledge metadata must be a YAML mapping: " + source
            );
        }

        Map<String, Object> metadata = new HashMap<>();
        yamlMetadata.forEach((key, value) -> {
            if (!(key instanceof String textKey) || textKey.isBlank() || value == null) {
                throw new IllegalStateException(
                        "Knowledge metadata contains an invalid YAML entry: " + source
                );
            }
            metadata.put(textKey, value);
        });
        return metadata;
    }

    private static Yaml createYamlParser() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    }

    private void validateOptionalCategory(Map<String, Object> metadata, String source) {
        Object value = metadata.get(KnowledgeMetadata.CATEGORY);
        if (value == null) {
            return;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException(
                    "Knowledge file has invalid metadata '"
                            + KnowledgeMetadata.CATEGORY + "': " + source
            );
        }
    }
}
