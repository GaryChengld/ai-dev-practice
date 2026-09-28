package com.example.aipractice.tools;

import com.example.aipractice.service.KnowledgeService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Exposes application knowledge that an AI model can retrieve for context.
 */
@Component
public class KnowledgeTools {

    private final KnowledgeService knowledgeService;

    /**
     * Creates knowledge tools backed by application knowledge resources.
     *
     * @param knowledgeService service used to retrieve knowledge
     */
    public KnowledgeTools(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * Retrieves the support ticket SLA policy.
     *
     * @return Markdown content of the SLA policy
     */
    @Tool(description = "Retrieve the company's support ticket SLA policy")
    public String getTicketSlaPolicy() {
        return knowledgeService.getTicketSlaPolicy();
    }
}
