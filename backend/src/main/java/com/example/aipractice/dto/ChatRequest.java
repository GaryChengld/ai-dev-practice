package com.example.aipractice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for an AI chat operation.
 *
 * @param message non-blank user message to send to the model
 */
public record ChatRequest(
        @NotBlank String message
) {
}
