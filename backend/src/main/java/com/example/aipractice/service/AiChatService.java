package com.example.aipractice.service;

import com.example.aipractice.config.AiPrompts;
import com.example.aipractice.dto.ChatResponse;
import com.example.aipractice.exception.AiProviderException;
import com.example.aipractice.exception.ConversationNotFoundException;
import com.example.aipractice.tools.TicketPriorityTools;
import com.example.aipractice.tools.TicketTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
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
    private final AiPrompts prompts;
    private final TicketTools ticketTools;
    private final TicketPriorityTools ticketPriorityTools;
    private final KnowledgeService knowledgeService;
    private final KnowledgeQueryRewriter queryRewriter;
    private final ConversationContextManager contextManager;
    private final Map<String, List<Message>> conversationHistory = new ConcurrentHashMap<>();

    /**
     * Creates the AI chat service.
     *
     * @param chatClient shared Spring AI chat client
     * @param prompts named prompt configuration for AI workflows
     * @param ticketTools ticket operations available to the AI model
     * @param ticketPriorityTools ticket priority operations available to the AI model
     * @param knowledgeService service that retrieves relevant application knowledge
     * @param queryRewriter service that makes follow-up questions standalone for retrieval
     * @param contextManager policy for selecting context for each AI operation
     */
    public AiChatService(
            ChatClient chatClient,
            AiPrompts prompts,
            TicketTools ticketTools,
            TicketPriorityTools ticketPriorityTools,
            KnowledgeService knowledgeService,
            KnowledgeQueryRewriter queryRewriter,
            ConversationContextManager contextManager
    ) {
        this.chatClient = chatClient;
        this.prompts = prompts;
        this.ticketTools = ticketTools;
        this.ticketPriorityTools = ticketPriorityTools;
        this.knowledgeService = knowledgeService;
        this.queryRewriter = queryRewriter;
        this.contextManager = contextManager;
    }

    /**
     * Sends a message with its conversation history to the AI provider.
     *
     * @param conversationId existing conversation identifier, or {@code null} or blank to start one
     * @param message user message to process
     * @return chat response containing the conversation identifier, generated text, and sources
     * @throws ConversationNotFoundException if a supplied conversation identifier is unknown
     * @throws AiProviderException if the model call returns no content or fails
     */
    public ChatResponse chat(String conversationId, String message) {
        String resolvedConversationId = resolveConversationId(conversationId);
        List<Message> history = conversationHistory.get(resolvedConversationId);

        synchronized (history) {
            try {
                UserMessage userMessage = new UserMessage(message);
                List<Message> rewriteContext = contextManager.forQueryRewrite(history);
                String searchQuery = history.isEmpty()
                        ? message
                        : queryRewriter.rewrite(message, rewriteContext);
                List<Document> relevantKnowledge = knowledgeService.search(searchQuery);
                List<Message> chatContext = contextManager.forChat(history);
                String augmentedMessage = addKnowledgeContext(message, relevantKnowledge);
                String response = chatClient.prompt()
                        .system(prompts.chatAssistant())
                        .messages(chatContext)
                        .user(augmentedMessage)
                        .tools(ticketTools, ticketPriorityTools)
                        .call()
                        .content();

                if (response == null || response.isBlank()) {
                    throw new AiProviderException("AI provider returned no chat content");
                }

                history.add(userMessage);
                history.add(new AssistantMessage(response));
                return new ChatResponse(
                        resolvedConversationId,
                        response,
                        extractSources(relevantKnowledge)
                );
            } catch (AiProviderException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new AiProviderException("AI chat request failed", exception);
            }
        }
    }

    private List<String> extractSources(List<Document> documents) {
        return documents.stream()
                .map(document -> document.getMetadata().get(KnowledgeMetadata.SOURCE))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(source -> !source.isBlank())
                .distinct()
                .toList();
    }

    private String addKnowledgeContext(String message, List<Document> relevantKnowledge) {
        if (relevantKnowledge.isEmpty()) {
            return message;
        }

        String context = relevantKnowledge.stream()
                .map(Document::getText)
                .reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("");

        return """
                Use the retrieved company knowledge below when it is relevant to the question.
                If the context does not answer the question, do not invent a company policy.

                <knowledge-context>
                %s
                </knowledge-context>

                User question:
                %s
                """.formatted(context, message);
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
