package com.example.aipractice.service;

import com.example.aipractice.config.AiPrompts;
import com.example.aipractice.exception.AiProviderException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rewrites a conversational follow-up into a standalone knowledge-search query.
 */
@Service
public class KnowledgeQueryRewriter {

    private final ChatClient chatClient;
    private final AiPrompts prompts;

    /**
     * Creates an AI-backed knowledge query rewriter.
     *
     * @param chatClient shared Spring AI chat client
     * @param prompts named prompt configuration for AI workflows
     */
    public KnowledgeQueryRewriter(ChatClient chatClient, AiPrompts prompts) {
        this.chatClient = chatClient;
        this.prompts = prompts;
    }

    /**
     * Rewrites the latest message using prior conversation context.
     *
     * @param message latest user message
     * @param history prior user and assistant messages
     * @return concise standalone query for knowledge retrieval
     * @throws AiProviderException if rewriting fails or returns no content
     */
    public String rewrite(String message, List<Message> history) {
        try {
            String rewrittenQuery = chatClient.prompt()
                    .system(prompts.knowledgeQueryRewriter())
                    .messages(history)
                    .user(message)
                    .call()
                    .content();

            if (rewrittenQuery == null || rewrittenQuery.isBlank()) {
                throw new AiProviderException("AI provider returned no rewritten query");
            }
            return rewrittenQuery.trim();
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderException("AI knowledge query rewrite failed", exception);
        }
    }
}
