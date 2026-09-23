package com.example.aipractice.controller;

import com.example.aipractice.dto.ApiError;
import com.example.aipractice.exception.AiProviderException;
import com.example.aipractice.exception.ConversationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts AI provider failures into consistent REST error responses.
 */
@RestControllerAdvice
public class AiExceptionHandler {

    /**
     * Maps an unknown conversation to an HTTP 404 response.
     *
     * @param exception exception raised for an unknown conversation identifier
     * @return structured API error response
     */
    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ApiError> handleConversationNotFound(
            ConversationNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("CONVERSATION_NOT_FOUND", exception.getMessage()));
    }

    /**
     * Maps a provider failure to an HTTP 502 response.
     *
     * @param exception provider exception raised while processing a request
     * @return structured API error response
     */
    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiError> handleProviderError(AiProviderException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("AI_PROVIDER_ERROR", exception.getMessage()));
    }
}
