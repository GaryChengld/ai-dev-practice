package com.example.aipractice.service;

import com.example.aipractice.config.AiPrompts;
import com.example.aipractice.service.TicketAgentService.AgentDecision;
import com.example.aipractice.service.TicketAgentService.AgentPlanner;
import com.example.aipractice.service.TicketAgentService.AgentStep;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Objects;

/**
 * Proposes structured decisions; the execution engine alone invokes ticket tools.
 * Supply a dedicated ChatClient without registered ticket tools.
 */
public class GeminiTicketAgentPlanner implements AgentPlanner {

    private final ChatClient chatClient;
    private final AiPrompts prompts;

    public GeminiTicketAgentPlanner(ChatClient chatClient, AiPrompts prompts) {
        this.chatClient = Objects.requireNonNull(chatClient);
        this.prompts = Objects.requireNonNull(prompts);
    }

    @Override
    public AgentDecision decide(String ticketId, List<AgentStep> previousSteps) {
        String userPrompt = """
                Ticket ID:
                %s

                Previous execution steps:
                %s

                Choose the next action.
                """.formatted(ticketId, previousSteps);

        return chatClient.prompt()
                .system(prompts.ticketAgentPlanner())
                .user(userPrompt)
                .call()
                .entity(AgentDecision.class);
    }
}
