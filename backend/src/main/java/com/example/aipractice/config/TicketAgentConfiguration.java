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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TicketAgentConfiguration {

    @Bean
    public ThreadPoolTaskExecutor agentStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("agent-stream-");
        return executor;
    }

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
