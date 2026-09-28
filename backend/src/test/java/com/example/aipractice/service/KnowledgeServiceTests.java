package com.example.aipractice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeServiceTests {

    private final KnowledgeService knowledgeService = new KnowledgeService();

    @Test
    void loadsTicketSlaPolicyFromTheClasspath() {
        assertThat(knowledgeService.getTicketSlaPolicy())
                .startsWith("# Support Ticket SLA")
                .contains(
                        "CRITICAL tickets require a response within 1 hour.",
                        "HIGH priority tickets require a response within 4 hours.",
                        "MEDIUM priority tickets require a response within 1 business day.",
                        "LOW priority tickets require a response within 3 business days."
                );
    }
}
