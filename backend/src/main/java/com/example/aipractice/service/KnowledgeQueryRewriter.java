package com.example.aipractice.service;

import com.example.aipractice.config.AiContextProperties;
import com.example.aipractice.config.AiPrompts;
import com.example.aipractice.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rewrites a conversational follow-up into a standalone knowledge-search query.
 */
@Service
public class KnowledgeQueryRewriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeQueryRewriter.class);
    private static final int MESSAGE_OVERHEAD_TOKENS = 8;

    private final ChatClient chatClient;
    private final AiPrompts prompts;
    private final TokenBudgetService tokenBudgetService;
    private final int maxInputTokens;

    /**
     * Creates an AI-backed knowledge query rewriter.
     *
     * @param chatClient shared Spring AI chat client
     * @param prompts named prompt configuration for AI workflows
     * @param tokenBudgetService service that estimates prompt token usage
     * @param contextProperties input-token budgets for context-management operations
     */
    public KnowledgeQueryRewriter(
            ChatClient chatClient,
            AiPrompts prompts,
            TokenBudgetService tokenBudgetService,
            AiContextProperties contextProperties
    ) {
        this.chatClient = chatClient;
        this.prompts = prompts;
        this.tokenBudgetService = tokenBudgetService;
        this.maxInputTokens = contextProperties.queryRewriteMaxInputTokens();
    }

    /**
     * Rewrites the latest message using prior conversation context.
     *
     * @param message latest user message
     * @param conversationSummary compacted context from earlier conversation messages
     * @param recentHistory recent user and assistant messages
     * @return concise standalone query for knowledge retrieval
     * @throws AiProviderException if rewriting fails or returns no content
     */
    public String rewrite(
            String message,
            String conversationSummary,
            List<Message> recentHistory
    ) {
        String rewriterPrompt = prompts.knowledgeQueryRewriter().replace(
                "{{conversationSummary}}",
                conversationSummary == null ? "" : conversationSummary
        );
        int estimatedTokens = tokenBudgetService.estimateTokens(rewriterPrompt)
                + MESSAGE_OVERHEAD_TOKENS
                + tokenBudgetService.estimateTokens(recentHistory)
                + tokenBudgetService.estimateTokens(message)
                + MESSAGE_OVERHEAD_TOKENS;

        if (!tokenBudgetService.fitsBudget(estimatedTokens, maxInputTokens)) {
            LOGGER.info(
                    "Skipping knowledge query rewrite: estimated input tokens {} exceed budget {}",
                    estimatedTokens,
                    maxInputTokens
            );
            return message;
        }

        try {
            String rewrittenQuery = chatClient.prompt()
                    .system(rewriterPrompt)
                    .messages(recentHistory)
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
