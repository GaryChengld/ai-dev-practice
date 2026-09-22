package com.example.aipractice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines shared Spring AI infrastructure used by application services.
 */
@Configuration
public class AiConfiguration {

    /**
     * Creates the reusable chat client used for stateless AI requests.
     *
     * @param builder auto-configured Spring AI chat client builder
     * @return shared chat client
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
