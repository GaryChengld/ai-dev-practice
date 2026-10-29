package com.example.aipractice.controller;

import com.example.aipractice.service.TicketAgentService;
import com.example.aipractice.service.TicketAgentService.AgentResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/agents/tickets")
public class TicketAgentStreamController {

    private final TicketAgentService agentService;
    private final Executor executor;

    public TicketAgentStreamController(
            TicketAgentService agentService,
            @Qualifier("agentStreamExecutor") Executor executor
    ) {
        this.agentService = agentService;
        this.executor = executor;
    }

    @PostMapping(
            value = "/analyze/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(@Valid @RequestBody TicketAgentController.AnalyzeRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        try {
            executor.execute(() -> streamResult(request.ticketId(), emitter));
        } catch (RejectedExecutionException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Agent streaming capacity is currently full", ex);
        }

        return emitter;
    }

    private void streamResult(String ticketId, SseEmitter emitter) {
        try {
            AgentResult result = agentService.run(ticketId, step -> send(emitter, "step", step));
            // Handled planner/tool failures are final agent results, too.
            send(emitter, "complete", result);
            emitter.complete();
        } catch (EventDeliveryException ex) {
            // Spring MVC and the servlet container clean up failed I/O writes.
            if (!(ex.getCause() instanceof IOException)) {
                emitter.completeWithError(ex.getCause());
            }
        } catch (RuntimeException ex) {
            // Unexpected execution failures must close the stream without inventing a result.
            emitter.completeWithError(ex);
        }
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            throw new EventDeliveryException(ex);
        }
    }

    /** Keeps transport failures outside the agent's planner and tool failure handling. */
    private static final class EventDeliveryException extends RuntimeException {
        private EventDeliveryException(Exception cause) {
            super("Unable to deliver agent stream event", cause);
        }
    }
}
