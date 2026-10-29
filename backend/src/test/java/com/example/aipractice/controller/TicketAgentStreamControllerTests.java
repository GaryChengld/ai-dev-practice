package com.example.aipractice.controller;

import com.example.aipractice.service.TicketAgentService;
import com.example.aipractice.service.TicketAgentService.Action;
import com.example.aipractice.service.TicketAgentService.AgentResult;
import com.example.aipractice.service.TicketAgentService.AgentStatus;
import com.example.aipractice.service.TicketAgentService.AgentStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketAgentStreamControllerTests {

    private final TicketAgentService agentService = mock(TicketAgentService.class);
    private final JsonMapper json = new JsonMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TicketAgentStreamController(agentService, Runnable::run))
                .build();
    }

    @Test
    void streamsToolStepsInOrderFollowedByCompletedResult() throws Exception {
        AgentStep statusStep = new AgentStep(Action.CHECK_STATUS, "OPEN");
        AgentStep priorityStep = new AgentStep(Action.CHECK_PRIORITY, "HIGH");
        AgentResult result = new AgentResult("Ticket requires immediate attention",
                List.of(statusStep, priorityStep), true, AgentStatus.COMPLETED);
        stubAgent(result);

        String[] events = streamEvents();

        assertThat(events).hasSize(3);
        assertThat(readEvent(events[0], "step", AgentStep.class)).isEqualTo(statusStep);
        assertThat(readEvent(events[1], "step", AgentStep.class)).isEqualTo(priorityStep);
        assertThat(readEvent(events[2], "complete", AgentResult.class)).isEqualTo(result);
        verify(agentService).run(eq("INC-1001"), any());
        verifyNoMoreInteractions(agentService);
    }

    @Test
    void completesWithStructuredToolFailureAndPreviouslyEmittedStep() throws Exception {
        AgentStep statusStep = new AgentStep(Action.CHECK_STATUS, "OPEN");
        AgentResult result = new AgentResult("Unable to retrieve ticket priority",
                List.of(statusStep), false, AgentStatus.TOOL_ERROR);
        stubAgent(result);

        String[] events = streamEvents();

        assertThat(events).hasSize(2);
        assertThat(readEvent(events[0], "step", AgentStep.class)).isEqualTo(statusStep);
        assertThat(readEvent(events[1], "complete", AgentResult.class)).isEqualTo(result);
    }

    @Test
    void completesWithStructuredPlannerFailureWithoutSteps() throws Exception {
        AgentResult result = new AgentResult("Unable to complete ticket analysis",
                List.of(), false, AgentStatus.PLANNER_ERROR);
        stubAgent(result);

        String[] events = streamEvents();

        assertThat(events).hasSize(1);
        assertThat(readEvent(events[0], "complete", AgentResult.class)).isEqualTo(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"ticketId\":\" \"}", "{}", "{\"ticketId\":null}", "{"})
    void rejectsInvalidRequestsBeforeRunningAgent(String body) throws Exception {
        mockMvc.perform(post("/api/agents/tickets/analyze/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(agentService);
    }

    @Test
    void returnsServiceUnavailableWhenExecutorIsFull() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketAgentStreamController(
                agentService, task -> { throw new RejectedExecutionException("Queue full"); }))
                .build();

        mockMvc.perform(post("/api/agents/tickets/analyze/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"INC-1001\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(agentService);
    }

    @Test
    void stopsDeliveryAfterAnIoFailureWithoutSendingACompletionEvent() throws Exception {
        IOException disconnected = new IOException("Client disconnected");
        stubAgent(new AgentResult("Done", List.of(
                new AgentStep(Action.CHECK_STATUS, "OPEN"),
                new AgentStep(Action.CHECK_PRIORITY, "HIGH")), true));

        try (var emitters = mockConstruction(SseEmitter.class, (emitter, context) ->
                doThrow(disconnected).when(emitter).send(any(SseEmitter.SseEventBuilder.class)))) {
            SseEmitter emitter = new TicketAgentStreamController(agentService, Runnable::run)
                    .stream(new TicketAgentController.AnalyzeRequest("INC-1001"));

            assertThat(emitters.constructed()).containsExactly(emitter);
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            // I/O cleanup belongs to Spring MVC, not the application worker.
            verify(emitter, never()).complete();
            verify(emitter, never()).completeWithError(any());
        }
    }

    @Test
    void closesStreamOnAnUnexpectedExecutionFailureWithoutInventingAResult() {
        IllegalStateException invalidAction = new IllegalStateException("Invalid action ordering");
        doThrow(invalidAction).when(agentService).run(eq("INC-1001"), any());

        try (var emitters = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = new TicketAgentStreamController(agentService, Runnable::run)
                    .stream(new TicketAgentController.AnalyzeRequest("INC-1001"));

            assertThat(emitters.constructed()).containsExactly(emitter);
            verify(emitter).completeWithError(invalidAction);
            verifyNoMoreInteractions(emitter);
        }
    }

    private void stubAgent(AgentResult result) {
        doAnswer(invocation -> {
            Consumer<AgentStep> onStep = invocation.getArgument(1);
            result.steps().forEach(onStep);
            return result;
        }).when(agentService).run(eq("INC-1001"), any());
    }

    private String[] streamEvents() throws Exception {
        MvcResult pending = mockMvc.perform(post("/api/agents/tickets/analyze/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"ticketId\":\"INC-1001\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn().getResponse().getContentAsString();
        return body.replace("\r\n", "\n").split("\n\n");
    }

    private <T> T readEvent(String event, String name, Class<T> type) {
        String prefix = "event:" + name + "\ndata:";
        assertThat(event).startsWith(prefix);
        return json.readValue(event.substring(prefix.length()), type);
    }
}
