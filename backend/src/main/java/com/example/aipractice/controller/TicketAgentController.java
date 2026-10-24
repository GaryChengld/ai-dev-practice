package com.example.aipractice.controller;

import com.example.aipractice.service.TicketAgentService;
import com.example.aipractice.service.TicketAgentService.AgentResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents/tickets")
public class TicketAgentController {

    private final TicketAgentService agentService;

    public TicketAgentController(TicketAgentService agentService) {
        this.agentService = agentService;
    }

    public record AnalyzeRequest(@NotBlank String ticketId) {}

    /**
     * Returns HTTP 200 for handled agent outcomes, including planner and tool failures.
     * Invalid request bodies are rejected by Spring MVC before the agent runs.
     */
    @PostMapping(
            value = "/analyze",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public AgentResult analyze(@Valid @RequestBody AnalyzeRequest request) {
        return agentService.run(request.ticketId());
    }
}
