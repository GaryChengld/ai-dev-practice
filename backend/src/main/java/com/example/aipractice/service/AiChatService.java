package com.example.aipractice.service;

import com.example.aipractice.client.AiClient;
import com.example.aipractice.config.AiProviderProperties;
import com.example.aipractice.dto.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Coordinates AI chat requests and builds application response payloads.
 */
@Service
public class AiChatService {

    private final AiClient aiClient;
    private final AiProviderProperties properties;

    /**
     * Creates the AI chat service.
     *
     * @param aiClient client used to communicate with the AI provider
     * @param properties application-level provider settings
     */
    public AiChatService(AiClient aiClient, AiProviderProperties properties) {
        this.aiClient = aiClient;
        this.properties = properties;
    }

    /**
     * Sends a message to the AI provider and packages its response.
     *
     * @param message user message to process
     * @return chat response containing generated text and model name
     */
    public ChatResponse chat(String message) {
        return new ChatResponse(aiClient.chat(message), properties.model());
    }
}
