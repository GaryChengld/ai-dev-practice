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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketAgentControllerTests {

    private final TicketAgentService agentService = mock(TicketAgentService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TicketAgentController(agentService))
                .build();
    }

    @Test
    void returnsCompletedAgentResult() throws Exception {
        AgentResult result = new AgentResult(
                "Ticket requires immediate attention",
                List.of(
                        new AgentStep(Action.CHECK_STATUS, "OPEN"),
                        new AgentStep(Action.CHECK_PRIORITY, "HIGH")
                ),
                true,
                AgentStatus.COMPLETED
        );
        when(agentService.run("INC-1001")).thenReturn(result);

        mockMvc.perform(post("/api/agents/tickets/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticketId":"INC-1001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.answer").value("Ticket requires immediate attention"))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].action").value("CHECK_STATUS"))
                .andExpect(jsonPath("$.steps[0].result").value("OPEN"))
                .andExpect(jsonPath("$.steps[1].action").value("CHECK_PRIORITY"))
                .andExpect(jsonPath("$.steps[1].result").value("HIGH"));

        verify(agentService).run("INC-1001");
        verifyNoMoreInteractions(agentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"ticketId\":\" \"}",
            "{\"ticketId\":\"\"}",
            "{\"ticketId\":null}",
            "{}",
            "null",
            "{"
    })
    void rejectsInvalidRequestBodies(String body) throws Exception {
        mockMvc.perform(post("/api/agents/tickets/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agentService);
    }

    @Test
    void returnsStructuredPlannerFailure() throws Exception {
        AgentResult result = new AgentResult(
                "Unable to complete ticket analysis",
                List.of(),
                false,
                AgentStatus.PLANNER_ERROR
        );
        when(agentService.run("INC-1001")).thenReturn(result);

        mockMvc.perform(post("/api/agents/tickets/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticketId":"INC-1001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.answer").value("Unable to complete ticket analysis"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.status").value("PLANNER_ERROR"))
                .andExpect(jsonPath("$.steps.length()").value(0));

        verify(agentService).run("INC-1001");
        verifyNoMoreInteractions(agentService);
    }
}
