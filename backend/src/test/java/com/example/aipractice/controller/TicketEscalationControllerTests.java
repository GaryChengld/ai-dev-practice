package com.example.aipractice.controller;

import com.example.aipractice.exception.ProposalNotPendingException;
import com.example.aipractice.service.ApprovalAuthorizer;
import com.example.aipractice.service.TicketAgentService;
import com.example.aipractice.service.TicketAgentService.Action;
import com.example.aipractice.service.TicketAgentService.AgentResult;
import com.example.aipractice.service.TicketAgentService.AgentStatus;
import com.example.aipractice.service.TicketAgentService.AgentStep;
import com.example.aipractice.service.TicketEscalationApprovalService;
import com.example.aipractice.service.TicketEscalationApprovalService.EscalationProposal;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketEscalationControllerTests {

    private static final String ENDPOINT = "/api/agents/tickets/escalation-proposals";
    private final TicketAgentService agentService = mock(TicketAgentService.class);
    private final TicketEscalationApprovalService approvalService =
            mock(TicketEscalationApprovalService.class);
    private final ApprovalAuthorizer authorizer = mock(ApprovalAuthorizer.class);
    private final Principal principal = () -> "approver";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketEscalationController(
                agentService, approvalService, authorizer
        )).build();
    }

    @Test
    void createsProposalFromServerSideAgentAnalysis() throws Exception {
        AgentResult analysis = new AgentResult("Requires attention", List.of(
                new AgentStep(Action.CHECK_STATUS, "OPEN"),
                new AgentStep(Action.CHECK_PRIORITY, "HIGH")
        ), true, AgentStatus.COMPLETED);
        EscalationProposal proposal = new EscalationProposal(
                UUID.randomUUID(), "INC-1001", "Completed analysis found HIGH priority"
        );
        when(agentService.run("INC-1001")).thenReturn(analysis);
        when(approvalService.propose("INC-1001", analysis)).thenReturn(Optional.of(proposal));

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticketId":"INC-1001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(proposal.id().toString()))
                .andExpect(jsonPath("$.ticketId").value("INC-1001"))
                .andExpect(jsonPath("$.reason").value(proposal.reason()));

        InOrder order = inOrder(agentService, approvalService);
        order.verify(agentService).run("INC-1001");
        order.verify(approvalService).propose("INC-1001", analysis);
        order.verifyNoMoreInteractions();
        verifyNoInteractions(authorizer);
    }

    @Test
    void returnsNoContentForIneligibleAnalysis() throws Exception {
        AgentResult analysis = failedAnalysis();
        when(agentService.run("INC-1001")).thenReturn(analysis);
        when(approvalService.propose("INC-1001", analysis)).thenReturn(Optional.empty());

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticketId":"INC-1001"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        InOrder order = inOrder(agentService, approvalService);
        order.verify(agentService).run("INC-1001");
        order.verify(approvalService).propose("INC-1001", analysis);
        order.verifyNoMoreInteractions();
    }

    @Test
    void ignoresFabricatedAnalysisAndProposalFieldsFromTheClient() throws Exception {
        AgentResult serverAnalysis = failedAnalysis();
        when(agentService.run("INC-1001")).thenReturn(serverAnalysis);
        when(approvalService.propose("INC-1001", serverAnalysis)).thenReturn(Optional.empty());

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticketId":"INC-1001",
                                  "id":"00000000-0000-0000-0000-000000000001",
                                  "action":"ESCALATE",
                                  "completed":true,
                                  "status":"COMPLETED",
                                  "steps":[{"action":"CHECK_PRIORITY","result":"HIGH"}],
                                  "analysis":{
                                    "answer":"Fabricated approval",
                                    "completed":true,
                                    "status":"COMPLETED",
                                    "steps":[{"action":"CHECK_PRIORITY","result":"HIGH"}]
                                  }
                                }
                                """))
                .andExpect(status().isNoContent());

        InOrder order = inOrder(agentService, approvalService);
        order.verify(agentService).run("INC-1001");
        order.verify(approvalService).propose("INC-1001", serverAnalysis);
        order.verifyNoMoreInteractions();
        verifyNoInteractions(authorizer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void authorizesBeforeConsumingTheStoredProposal(String operation) throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(ENDPOINT + "/{id}/{operation}", id, operation)
                        .principal(principal))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        InOrder order = inOrder(authorizer, approvalService);
        order.verify(authorizer).requireCanEscalate(principal);
        if (operation.equals("approve")) {
            order.verify(approvalService).approve(id);
        } else {
            order.verify(approvalService).reject(id);
        }
        order.verifyNoMoreInteractions();
        verifyNoInteractions(agentService);
    }

    @Test
    void approvalUsesOnlyThePathIdEvenIfAClientSendsAReplacementTicketOrAction() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(ENDPOINT + "/{id}/approve", id).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticketId":"INC-9999","action":"DELETE_DATABASE"}
                                """))
                .andExpect(status().isNoContent());

        InOrder order = inOrder(authorizer, approvalService);
        order.verify(authorizer).requireCanEscalate(principal);
        order.verify(approvalService).approve(id);
        order.verifyNoMoreInteractions();
        verifyNoInteractions(agentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void deniedAuthorizationDoesNotConsumeTheProposal(String operation) throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(authorizer).requireCanEscalate(principal);

        mockMvc.perform(post(ENDPOINT + "/{id}/{operation}", UUID.randomUUID(), operation)
                        .principal(principal))
                .andExpect(status().isForbidden());

        InOrder order = inOrder(authorizer);
        order.verify(authorizer).requireCanEscalate(principal);
        order.verifyNoMoreInteractions();
        verifyNoInteractions(agentService, approvalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void rejectsUnauthenticatedConsumption(String operation) throws Exception {
        mockMvc.perform(post(ENDPOINT + "/{id}/{operation}", UUID.randomUUID(), operation))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(agentService, approvalService, authorizer);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"ticketId\":\" \"}", "{\"ticketId\":\"\"}", "{\"ticketId\":null}", "{}", "null", "{"
    })
    void rejectsInvalidProposalRequestsBeforeRunningTheAgent(String body) throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agentService, approvalService, authorizer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void rejectsMalformedProposalIds(String operation) throws Exception {
        mockMvc.perform(post(ENDPOINT + "/not-a-uuid/" + operation).principal(principal))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agentService, approvalService, authorizer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void returnsConflictOnlyForANoLongerPendingProposal(String operation) throws Exception {
        UUID id = UUID.randomUUID();
        if (operation.equals("approve")) {
            doThrow(new ProposalNotPendingException()).when(approvalService).approve(id);
        } else {
            doThrow(new ProposalNotPendingException()).when(approvalService).reject(id);
        }

        mockMvc.perform(post(ENDPOINT + "/{id}/{operation}", id, operation).principal(principal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_PENDING"))
                .andExpect(jsonPath("$.message").value("Proposal is no longer pending"));
    }

    @Test
    void leavesGatewayFailuresAsUnhandledServerErrorsRatherThanConflicts() {
        UUID id = UUID.randomUUID();
        IllegalStateException gatewayFailure = new IllegalStateException("Gateway unavailable");
        doThrow(gatewayFailure).when(approvalService).approve(id);

        // Standalone MockMvc propagates unhandled exceptions; the servlet container returns 500.
        assertThatThrownBy(() -> mockMvc.perform(post(ENDPOINT + "/{id}/approve", id)
                        .principal(principal)))
                .isInstanceOf(ServletException.class)
                .hasRootCause(gatewayFailure);
    }

    @Test
    void isNotRegisteredOutsideTheDemoProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(TicketEscalationController.class);
            context.refresh();

            assertThat(context.getBeansOfType(TicketEscalationController.class)).isEmpty();
        }
    }

    @Test
    void isRegisteredInTheDemoProfileWithExplicitlySuppliedDependencies() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("approval-demo");
            context.registerBean(TicketAgentService.class, () -> agentService);
            context.registerBean(TicketEscalationApprovalService.class, () -> approvalService);
            context.registerBean(ApprovalAuthorizer.class, () -> authorizer);
            context.register(TicketEscalationController.class);
            context.refresh();

            assertThat(context.getBeansOfType(TicketEscalationController.class)).hasSize(1);
            verifyNoInteractions(agentService, approvalService, authorizer);
        }
    }

    private AgentResult failedAnalysis() {
        return new AgentResult("Unable to complete ticket analysis", List.of(),
                false, AgentStatus.PLANNER_ERROR);
    }
}
