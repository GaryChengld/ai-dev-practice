package com.example.aipractice.service;

import com.example.aipractice.config.AiPrompts;
import com.example.aipractice.exception.AiProviderException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Uses structured AI output to select a knowledge category for a message.
 */
@Service
public class KnowledgeCategoryRouter {

    public static final String NO_CATEGORY = "none";

    private final ChatClient chatClient;
    private final AiPrompts prompts;

    /**
     * Creates an AI-backed knowledge category router.
     *
     * @param chatClient shared Spring AI chat client
     * @param prompts named prompt configuration for AI workflows
     */
    public KnowledgeCategoryRouter(ChatClient chatClient, AiPrompts prompts) {
        this.chatClient = chatClient;
        this.prompts = prompts;
    }

    /**
     * Classifies a message into an allowed knowledge category.
     *
     * @param message user message to classify
     * @param availableCategories categories discovered from knowledge metadata
     * @return structured routing decision
     * @throws AiProviderException if routing fails or returns an unsupported category
     */
    public RoutingDecision route(String message, Set<String> availableCategories) {
        try {
            String systemPrompt = renderPrompt(availableCategories);
            RoutingDecision decision = chatClient.prompt()
                    .system(systemPrompt)
                    .user(message)
                    .call()
                    .entity(RoutingDecision.class);

            if (decision == null
                    || decision.category() == null
                    || decision.category().isBlank()
                    || (!NO_CATEGORY.equals(decision.category())
                    && !availableCategories.contains(decision.category()))) {
                throw new AiProviderException("AI provider returned an invalid routing decision");
            }
            return decision;
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderException("AI knowledge category routing failed", exception);
        }
    }

    private String renderPrompt(Set<String> availableCategories) {
        String categoryList = availableCategories.stream()
                .sorted()
                .map(category -> "- " + category)
                .collect(Collectors.joining("\n"));
        if (categoryList.isEmpty()) {
            categoryList = "(no categories available)";
        }
        return prompts.knowledgeCategoryRouter().replace("{{categories}}", categoryList);
    }
}
