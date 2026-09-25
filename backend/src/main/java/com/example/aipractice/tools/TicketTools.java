package com.example.aipractice.tools;

import com.example.aipractice.service.TicketService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Exposes ticket lookup operations that an AI model can call when answering users.
 */
@Component
public class TicketTools {

    private final TicketService ticketService;

    /**
     * Creates the ticket tools backed by application ticket data.
     *
     * @param ticketService service used to retrieve ticket information
     */
    public TicketTools(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * Gets the current status for a support ticket.
     *
     * @param ticketId ticket identifier, such as {@code INC-1001}
     * @return the ticket status, or {@code NOT_FOUND} when the ticket does not exist
     */
    @Tool(description = "Get the current status of a support ticket by its ticket ID")
    public String getTicketStatus(
            @ToolParam(description = "Support ticket ID, for example INC-1001") String ticketId
    ) {
        return ticketService.findTicketStatus(ticketId).orElse("NOT_FOUND");
    }
}
