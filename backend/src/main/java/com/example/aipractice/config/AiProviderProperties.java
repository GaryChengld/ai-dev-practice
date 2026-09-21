package com.example.aipractice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level AI provider settings used in API responses.
 *
 * @param model configured Gemini model name
 * @param systemPrompt instruction included with every chat request
 */
@ConfigurationProperties(prefix = "ai.provider")
public record AiProviderProperties(
        String model,
        String systemPrompt
) {
}
