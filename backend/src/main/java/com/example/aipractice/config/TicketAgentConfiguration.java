package com.example.aipractice.config;

import com.example.aipractice.service.GeminiTicketAgentPlanner;
import com.example.aipractice.service.TicketAgentService;
import com.example.aipractice.tools.TicketPriorityTools;
import com.example.aipractice.tools.TicketTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketAgentConfiguration {

    @Bean
    public TicketAgentService.AgentPlanner ticketAgentPlanner(ChatModel chatModel, AiPrompts prompts) {
        // The planner proposes actions; only the execution service invokes ticket tools.
        ChatClient plannerClient = ChatClient.create(chatModel);
        return new GeminiTicketAgentPlanner(plannerClient, prompts);
    }

    @Bean
    public TicketAgentService ticketAgentService(
            TicketTools ticketTools,
            TicketPriorityTools priorityTools,
            TicketAgentService.AgentPlanner planner,
            @Value("${ai.agent.max-steps:3}") int maxSteps
    ) {
        return new TicketAgentService(ticketTools, priorityTools, planner, maxSteps);
    }
}
