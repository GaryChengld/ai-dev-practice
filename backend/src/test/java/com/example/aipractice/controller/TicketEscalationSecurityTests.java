package com.example.aipractice.controller;

import com.example.aipractice.config.ApprovalSecurityConfiguration;
import com.example.aipractice.service.TicketAgentService;
import com.example.aipractice.service.TicketAgentService.AgentResult;
import com.example.aipractice.service.TicketEscalationApprovalService;
import com.example.aipractice.service.TicketEscalationApprovalService.EscalationProposal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(TicketEscalationSecurityTests.TestConfiguration.class)
@ActiveProfiles("approval-demo")
class TicketEscalationSecurityTests {

    private static final String ENDPOINT = "/api/agents/tickets/escalation-proposals";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TicketAgentService agentService;

    @Autowired
    private TicketEscalationApprovalService approvalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(agentService, approvalService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void unauthenticatedCallerCannotCreateProposal() throws Exception {
        mockMvc.perform(post(ENDPOINT).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"INC-1001\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(agentService, approvalService);
    }

    @Test
    void suppliedPrincipalAndUsernameHeaderDoNotAuthenticateCaller() throws Exception {
        mockMvc.perform(post(ENDPOINT + "/{id}/approve", UUID.randomUUID()).with(csrf())
                        .principal(() -> "approver").header("X-Username", "approver"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(agentService, approvalService);
    }

    @Test
    @WithMockUser(authorities = "TICKET_ESCALATE")
    void escalationPermissionDoesNotGrantReviewPermission() throws Exception {
        mockMvc.perform(post(ENDPOINT).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"INC-1001\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agentService, approvalService);
    }

    @Test
    @WithMockUser(authorities = "TICKET_REVIEW")
    void reviewerCreatesProposalFromServerAnalysis() throws Exception {
        AgentResult analysis = new AgentResult("Requires attention", List.of(), true);
        EscalationProposal proposal = new EscalationProposal(
                UUID.randomUUID(), "INC-1001", "HIGH priority requires review"
        );
        when(agentService.run("INC-1001")).thenReturn(analysis);
        when(approvalService.propose("INC-1001", analysis)).thenReturn(Optional.of(proposal));

        mockMvc.perform(post(ENDPOINT).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"INC-1001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(proposal.id().toString()));

        verify(agentService).run("INC-1001");
        verify(approvalService).propose("INC-1001", analysis);
        verifyNoMoreInteractions(agentService, approvalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    @WithMockUser(authorities = "TICKET_REVIEW")
    void reviewerCannotConsumeProposal(String operation) throws Exception {
        mockMvc.perform(post(ENDPOINT + "/{id}/{operation}", UUID.randomUUID(), operation)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agentService, approvalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    @WithMockUser(authorities = "TICKET_ESCALATE")
    void escalatorConsumesProposalExactlyOnce(String operation) throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(ENDPOINT + "/{id}/{operation}", id, operation).with(csrf()))
                .andExpect(status().isNoContent());

        if (operation.equals("approve")) {
            verify(approvalService).approve(id);
        } else {
            verify(approvalService).reject(id);
        }
        verifyNoMoreInteractions(approvalService);
        verifyNoInteractions(agentService);
    }

    @Test
    @WithMockUser(authorities = "TICKET_REVIEW")
    void authorizedReviewWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"INC-1001\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agentService, approvalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    @WithMockUser(authorities = "TICKET_ESCALATE")
    void authorizedDecisionWithoutCsrfIsRejected(String operation) throws Exception {
        mockMvc.perform(post(ENDPOINT + "/{id}/{operation}", UUID.randomUUID(), operation))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agentService, approvalService);
    }

    @Test
    @WithMockUser(authorities = "TICKET_ESCALATE")
    void invalidCsrfIsRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT + "/{id}/approve", UUID.randomUUID())
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agentService, approvalService);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({ApprovalSecurityConfiguration.class, TicketEscalationController.class})
    static class TestConfiguration {

        @Bean
        TicketAgentService agentService() {
            return mock(TicketAgentService.class);
        }

        @Bean
        TicketEscalationApprovalService approvalService() {
            return mock(TicketEscalationApprovalService.class);
        }
    }
}
