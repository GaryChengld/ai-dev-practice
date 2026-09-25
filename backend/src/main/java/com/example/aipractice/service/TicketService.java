package com.example.aipractice.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Provides ticket data from a small in-memory collection used for tool-calling practice.
 */
@Service
public class TicketService {

    private final Map<String, String> ticketStatuses = Map.of(
            "INC-1001", "OPEN",
            "INC-1002", "IN_PROGRESS",
            "INC-1003", "RESOLVED"
    );

    /**
     * Finds the current status of a ticket.
     *
     * @param ticketId ticket identifier to look up
     * @return ticket status when the identifier exists
     */
    public Optional<String> findTicketStatus(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                ticketStatuses.get(ticketId.trim().toUpperCase(Locale.ROOT))
        );
    }
}
