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

    private final Map<String, String> ticketPriorities = Map.of(
            "INC-1001", "HIGH",
            "INC-1002", "MEDIUM",
            "INC-1003", "LOW"
    );

    /**
     * Finds the current status of a ticket.
     *
     * @param ticketId ticket identifier to look up
     * @return ticket status when the identifier exists
     */
    public Optional<String> findTicketStatus(String ticketId) {
        return findTicketValue(ticketStatuses, ticketId);
    }

    /**
     * Finds the current priority of a ticket.
     *
     * @param ticketId ticket identifier to look up
     * @return ticket priority when the identifier exists
     */
    public Optional<String> findTicketPriority(String ticketId) {
        return findTicketValue(ticketPriorities, ticketId);
    }

    private Optional<String> findTicketValue(Map<String, String> ticketValues, String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                ticketValues.get(ticketId.trim().toUpperCase(Locale.ROOT))
        );
    }
}
