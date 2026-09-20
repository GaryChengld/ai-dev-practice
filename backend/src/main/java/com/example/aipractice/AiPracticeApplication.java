package com.example.aipractice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the AI practice Spring Boot application.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiPracticeApplication {

    /**
     * Starts the Spring application context and embedded web server.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(AiPracticeApplication.class, args);
    }
}
