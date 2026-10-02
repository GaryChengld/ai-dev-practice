package com.example.aipractice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Defines shared Spring AI infrastructure used by application services.
 */
@Configuration
public class AiConfiguration {

    /**
     * Creates the reusable chat client used for stateless AI requests.
     *
     * @param builder auto-configured Spring AI chat client builder
     * @return shared chat client
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * Creates an in-memory vector store for application knowledge.
     *
     * @param embeddingModel model used to embed knowledge chunks and queries
     * @return vector store used for similarity search
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * Loads configured Markdown prompt files from the classpath.
     *
     * @param properties configured prompt filenames
     * @return prompt content used by AI services
     */
    @Bean
    public AiPrompts aiPrompts(AiPromptProperties properties) {
        return new AiPrompts(
                loadPrompt(properties.chatAssistant()),
                loadPrompt(properties.ticketAnalyzer()),
                loadPrompt(properties.knowledgeCategoryRouter())
        );
    }

    private String loadPrompt(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalStateException("AI prompt filename must not be blank");
        }
        if (!filename.endsWith(".md") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalStateException("AI prompt must be a Markdown filename: " + filename);
        }

        ClassPathResource resource = new ClassPathResource("prompts/" + filename);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load AI prompt: " + filename, exception);
        }
    }
}
