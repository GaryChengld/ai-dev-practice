package com.example.aipractice.tools;

import com.example.aipractice.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;

class TicketPriorityToolsTests {

    private final TicketPriorityTools ticketPriorityTools =
            new TicketPriorityTools(new TicketService());

    @Test
    void returnsPriorityForKnownTickets() {
        assertThat(ticketPriorityTools.getTicketPriority("INC-1001")).isEqualTo("HIGH");
        assertThat(ticketPriorityTools.getTicketPriority("INC-1002")).isEqualTo("MEDIUM");
        assertThat(ticketPriorityTools.getTicketPriority("INC-1003")).isEqualTo("LOW");
    }

    @Test
    void normalizesTicketIds() {
        assertThat(ticketPriorityTools.getTicketPriority(" inc-1001 ")).isEqualTo("HIGH");
    }

    @Test
    void returnsNotFoundForUnknownTickets() {
        assertThat(ticketPriorityTools.getTicketPriority("INC-1024")).isEqualTo("NOT_FOUND");
    }

    @Test
    void limitsAttentionLookupsToActiveTickets() throws NoSuchMethodException {
        Tool tool = TicketPriorityTools.class
                .getMethod("getTicketPriority", String.class)
                .getAnnotation(Tool.class);

        assertThat(tool.description())
                .contains("only after its status is known to be OPEN or IN_PROGRESS");
    }
}
