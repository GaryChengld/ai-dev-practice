package com.example.aipractice.service;

import com.example.aipractice.config.AiPromptProperties;
import com.example.aipractice.dto.ChatResponse;
import com.example.aipractice.exception.AiProviderException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Coordinates AI chat requests and builds application response payloads.
 */
@Service
public class AiChatService {

    private final ChatClient chatClient;
    private final AiPromptProperties prompts;

    /**
     * Creates the AI chat service.
     *
     * @param chatClient shared Spring AI chat client
     * @param prompts named prompt configuration for AI workflows
     */
    public AiChatService(ChatClient chatClient, AiPromptProperties prompts) {
        this.chatClient = chatClient;
        this.prompts = prompts;
    }

    /**
     * Sends a message to the AI provider and packages its response.
     *
     * @param message user message to process
     * @return chat response containing generated text
     * @throws AiProviderException if the model call returns no content or fails
     */
    public ChatResponse chat(String message) {
        try {
            String response = chatClient.prompt()
                    .system(prompts.chatAssistant())
                    .user(message)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                throw new AiProviderException("AI provider returned no chat content");
            }
            return new ChatResponse(response);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderException("AI chat request failed", exception);
        }
    }
}
