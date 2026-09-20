package com.example.aipractice.dto;

/**
 * Error payload returned by the REST API.
 *
 * @param code stable machine-readable error code
 * @param message human-readable error description
 */
public record ApiError(
        String code,
        String message
) {
}
