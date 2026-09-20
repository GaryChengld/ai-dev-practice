package com.example.aipractice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level AI provider settings used in API responses.
 *
 * @param model configured Gemini model name
 */
@ConfigurationProperties(prefix = "ai.provider")
public record AiProviderProperties(
        String model
) {
}
