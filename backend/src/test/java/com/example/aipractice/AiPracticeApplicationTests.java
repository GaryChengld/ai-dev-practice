package com.example.aipractice;

import com.example.aipractice.config.AiPromptProperties;
import com.example.aipractice.config.AiPrompts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.google.genai.api-key=test-api-key")
class AiPracticeApplicationTests {

    @Autowired
    private AiPromptProperties prompts;

    @Autowired
    private AiPrompts promptContent;

    @Test
    void contextLoads() {
    }

    @Test
    void configuresConditionalTicketAttentionWorkflow() {
        assertThat(prompts.chatAssistant()).isEqualTo("chat-assistant.md");
        assertThat(prompts.ticketAnalyzer()).isEqualTo("ticket-analyzer.md");

        String normalizedPrompt = promptContent.chatAssistant().replaceAll("[`\\s]+", " ");

        assertThat(normalizedPrompt)
                .contains(
                        "always call getTicketStatus first",
                        "status is OPEN or IN_PROGRESS",
                        "do not call getTicketPriority",
                        "CRITICAL or HIGH priority means it needs attention",
                        "MEDIUM means it should be monitored but is not urgent",
                        "LOW means it generally does not need immediate attention"
                );
        assertThat(promptContent.ticketAnalyzer())
                .contains("You analyze software support tickets.");
    }
}
