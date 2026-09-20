package com.example.aipractice.controller;

import com.example.aipractice.dto.ChatRequest;
import com.example.aipractice.dto.ChatResponse;
import com.example.aipractice.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes REST endpoints for submitting chat messages to the AI service.
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * Creates the chat controller.
     *
     * @param aiChatService service that coordinates AI chat requests
     */
    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * Processes a validated JSON chat request.
     *
     * @param request incoming chat request
     * @return generated response and model information
     */
    @PostMapping(
            value = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return aiChatService.chat(request.message());
    }
}
