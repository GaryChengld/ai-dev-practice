package com.example.aipractice.tools;

import com.example.aipractice.service.TicketService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Exposes ticket priority lookups that an AI model can select when answering users.
 */
@Component
public class TicketPriorityTools {

    private final TicketService ticketService;

    /**
     * Creates the priority tools backed by application ticket data.
     *
     * @param ticketService service used to retrieve ticket information
     */
    public TicketPriorityTools(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * Gets the current priority for a support ticket.
     *
     * @param ticketId ticket identifier, such as {@code INC-1001}
     * @return the ticket priority, or {@code NOT_FOUND} when the ticket does not exist
     */
    @Tool(description = "Get the current priority of a support ticket by its ticket ID")
    public String getTicketPriority(
            @ToolParam(description = "Support ticket ID, for example INC-1001") String ticketId
    ) {
        return ticketService.findTicketPriority(ticketId).orElse("NOT_FOUND");
    }
}
