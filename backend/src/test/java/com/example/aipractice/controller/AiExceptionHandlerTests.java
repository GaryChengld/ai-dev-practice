package com.example.aipractice.controller;

import com.example.aipractice.dto.ApiError;
import com.example.aipractice.exception.ConversationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AiExceptionHandlerTests {

    private final AiExceptionHandler handler = new AiExceptionHandler();

    @Test
    void mapsAnUnknownConversationToNotFound() {
        ResponseEntity<ApiError> response = handler.handleConversationNotFound(
                new ConversationNotFoundException("missing-id")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ApiError(
                "CONVERSATION_NOT_FOUND",
                "Conversation not found: missing-id"
        ));
    }
}
