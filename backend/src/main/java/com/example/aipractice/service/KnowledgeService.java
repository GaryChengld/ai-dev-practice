package com.example.aipractice.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Provides application knowledge that can be retrieved for AI responses.
 */
@Service
public class KnowledgeService {

    private static final String TICKET_SLA_RESOURCE = "knowledge/ticket-sla.md";
    private volatile String ticketSlaPolicy;

    /**
     * Retrieves the company's support ticket SLA policy.
     *
     * @return Markdown content of the SLA policy
     * @throws IllegalStateException if the policy cannot be loaded
     */
    public String getTicketSlaPolicy() {
        String policy = ticketSlaPolicy;
        if (policy == null) {
            synchronized (this) {
                policy = ticketSlaPolicy;
                if (policy == null) {
                    policy = loadTicketSlaPolicy();
                    ticketSlaPolicy = policy;
                }
            }
        }
        return policy;
    }

    private String loadTicketSlaPolicy() {
        ClassPathResource resource = new ClassPathResource(TICKET_SLA_RESOURCE);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load ticket SLA policy", exception);
        }
    }
}
