package com.example.aipractice.service;

import com.example.aipractice.config.AiPromptProperties;
import com.example.aipractice.dto.ChatResponse;
import com.example.aipractice.exception.AiProviderException;
import com.example.aipractice.exception.ConversationNotFoundException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates AI chat requests and builds application response payloads.
 */
@Service
public class AiChatService {

    private final ChatClient chatClient;
    private final AiPromptProperties prompts;
    private final Map<String, List<Message>> conversationHistory = new ConcurrentHashMap<>();

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
     * Sends a message with its conversation history to the AI provider.
     *
     * @param conversationId existing conversation identifier, or {@code null} or blank to start one
     * @param message user message to process
     * @return chat response containing the conversation identifier and generated text
     * @throws ConversationNotFoundException if a supplied conversation identifier is unknown
     * @throws AiProviderException if the model call returns no content or fails
     */
    public ChatResponse chat(String conversationId, String message) {
        String resolvedConversationId = resolveConversationId(conversationId);
        List<Message> history = conversationHistory.get(resolvedConversationId);

        synchronized (history) {
            try {
                UserMessage userMessage = new UserMessage(message);
                String response = chatClient.prompt()
                        .system(prompts.chatAssistant())
                        .messages(history)
                        .user(message)
                        .call()
                        .content();

                if (response == null || response.isBlank()) {
                    throw new AiProviderException("AI provider returned no chat content");
                }

                history.add(userMessage);
                history.add(new AssistantMessage(response));
                return new ChatResponse(resolvedConversationId, response);
            } catch (AiProviderException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new AiProviderException("AI chat request failed", exception);
            }
        }
    }

    /**
     * Creates a conversation identifier or validates an existing one.
     *
     * @param conversationId requested conversation identifier
     * @return generated or validated conversation identifier
     * @throws ConversationNotFoundException if a supplied conversation identifier is unknown
     */
    private String resolveConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            String generatedId = UUID.randomUUID().toString();
            conversationHistory.put(
                    generatedId,
                    Collections.synchronizedList(new ArrayList<>())
            );
            return generatedId;
        }

        if (!conversationHistory.containsKey(conversationId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        return conversationId;
    }
}
