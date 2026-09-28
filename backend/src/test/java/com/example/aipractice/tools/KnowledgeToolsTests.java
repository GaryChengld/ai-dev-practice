package com.example.aipractice.tools;

import com.example.aipractice.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeToolsTests {

    private final KnowledgeTools knowledgeTools = new KnowledgeTools(new KnowledgeService());

    @Test
    void returnsTheTicketSlaPolicy() {
        assertThat(knowledgeTools.getTicketSlaPolicy())
                .contains("HIGH priority tickets require a response within 4 hours.");
    }

    @Test
    void describesTheKnowledgeAvailableToTheModel() throws NoSuchMethodException {
        Tool tool = KnowledgeTools.class
                .getMethod("getTicketSlaPolicy")
                .getAnnotation(Tool.class);

        assertThat(tool.description())
                .isEqualTo("Retrieve the company's support ticket SLA policy");
    }
}
