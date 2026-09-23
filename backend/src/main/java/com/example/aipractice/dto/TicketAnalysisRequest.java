package com.example.aipractice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for support ticket analysis.
 *
 * @param message non-blank support ticket description
 */
public record TicketAnalysisRequest(
        @NotBlank String message
) {
}
