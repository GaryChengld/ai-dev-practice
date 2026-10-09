package com.example.aipractice.service;

import com.example.aipractice.config.AiPrompts;
import com.example.aipractice.exception.AiProviderException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Uses the AI model to incrementally compact older conversation messages.
 */
@Service
public class ConversationSummaryService {

    private final ChatClient chatClient;
    private final AiPrompts prompts;

    /**
     * Creates an AI-backed rolling conversation summarizer.
     *
     * @param chatClient shared Spring AI chat client
     * @param prompts named prompt configuration for AI workflows
     */
    public ConversationSummaryService(ChatClient chatClient, AiPrompts prompts) {
        this.chatClient = chatClient;
        this.prompts = prompts;
    }

    /**
     * Updates a rolling summary with messages that have moved out of recent context.
     *
     * @param existingSummary summary of messages compacted previously
     * @param messagesToCompact newly old messages to incorporate
     * @return updated summary covering both inputs
     * @throws AiProviderException if summarization fails or returns no content
     */
    public String summarize(String existingSummary, List<Message> messagesToCompact) {
        if (messagesToCompact.isEmpty()) {
            return existingSummary == null ? "" : existingSummary;
        }
        try {
            String summary = chatClient.prompt()
                    .system(buildRequest(existingSummary, messagesToCompact))
                    .call()
                    .content();

            if (summary == null || summary.isBlank()) {
                throw new AiProviderException("AI provider returned no conversation summary");
            }
            return summary.trim();
        } catch (AiProviderException exception) {
            throw exception;
        } catch (TransientAiException | NonTransientAiException exception) {
            throw new AiProviderException("AI conversation summarization failed", exception);
        }
    }

    private String buildRequest(String existingSummary, List<Message> messagesToCompact) {
        String priorSummary = existingSummary == null || existingSummary.isBlank()
                ? "(none)"
                : existingSummary;
        String transcript = messagesToCompact.stream()
                .map(message -> message.getMessageType() + ":\n" + message.getText())
                .collect(Collectors.joining("\n\n"));

        return prompts.conversationSummarizer()
                .replace("{{existingSummary}}", priorSummary)
                .replace("{{messagesToCompact}}", transcript);
    }
}
