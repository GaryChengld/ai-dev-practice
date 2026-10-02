package com.example.aipractice.service;

import com.example.aipractice.config.AiPrompts;
import com.example.aipractice.exception.AiProviderException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Uses structured AI output to select a knowledge category for a message.
 */
@Service
public class KnowledgeCategoryRouter {

    public static final String NO_CATEGORY = "none";

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "ticket",
            "refund",
            "security",
            NO_CATEGORY
    );

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
     * @return structured routing decision
     * @throws AiProviderException if routing fails or returns an unsupported category
     */
    public RoutingDecision route(String message) {
        try {
            RoutingDecision decision = chatClient.prompt()
                    .system(prompts.knowledgeCategoryRouter())
                    .user(message)
                    .call()
                    .entity(RoutingDecision.class);

            if (decision == null
                    || decision.category() == null
                    || !ALLOWED_CATEGORIES.contains(decision.category())) {
                throw new AiProviderException("AI provider returned an invalid routing decision");
            }
            return decision;
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderException("AI knowledge category routing failed", exception);
        }
    }
}
