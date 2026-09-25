package com.example.aipractice.tools;

import com.example.aipractice.service.TicketService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketToolsTests {

    private final TicketTools ticketTools = new TicketTools(new TicketService());

    @Test
    void returnsStatusForKnownTickets() {
        assertThat(ticketTools.getTicketStatus("INC-1001")).isEqualTo("OPEN");
        assertThat(ticketTools.getTicketStatus("INC-1002")).isEqualTo("IN_PROGRESS");
        assertThat(ticketTools.getTicketStatus("INC-1003")).isEqualTo("RESOLVED");
    }

    @Test
    void normalizesTicketIds() {
        assertThat(ticketTools.getTicketStatus(" inc-1001 ")).isEqualTo("OPEN");
    }

    @Test
    void returnsNotFoundForUnknownTickets() {
        assertThat(ticketTools.getTicketStatus("INC-1024")).isEqualTo("NOT_FOUND");
    }
}
