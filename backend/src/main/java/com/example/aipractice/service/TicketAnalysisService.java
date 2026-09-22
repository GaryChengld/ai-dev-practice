package com.example.aipractice.service;

import com.example.aipractice.config.AiPromptProperties;
import com.example.aipractice.domain.TicketAnalysis;
import com.example.aipractice.exception.AiProviderException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Analyzes support tickets with Spring AI structured output.
 */
@Service
public class TicketAnalysisService {

    private final ChatClient chatClient;
    private final AiPromptProperties prompts;

    /**
     * Creates the ticket analysis service.
     *
     * @param chatClient shared Spring AI chat client
     * @param prompts named prompt configuration for AI workflows
     */
    public TicketAnalysisService(ChatClient chatClient, AiPromptProperties prompts) {
        this.chatClient = chatClient;
        this.prompts = prompts;
    }

    /**
     * Classifies and summarizes a support ticket as a typed domain object.
     *
     * @param message support ticket text to analyze
     * @return structured ticket analysis
     * @throws AiProviderException if the model call or output conversion fails
     */
    public TicketAnalysis analyze(String message) {
        try {
            TicketAnalysis analysis = chatClient.prompt()
                    .system(prompts.ticketAnalyzer())
                    .user(message)
                    .call()
                    .entity(TicketAnalysis.class);

            if (analysis == null) {
                throw new AiProviderException("AI provider returned no ticket analysis");
            }
            return analysis;
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderException("AI ticket analysis failed", exception);
        }
    }
}
