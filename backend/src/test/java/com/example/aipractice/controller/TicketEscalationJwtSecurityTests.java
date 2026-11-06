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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

@SpringJUnitWebConfig(TicketEscalationJwtSecurityTests.TestConfiguration.class)
@ActiveProfiles("approval-demo")
class TicketEscalationJwtSecurityTests {

    private static final String ENDPOINT = "/api/agents/tickets/escalation-proposals";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TicketAgentService agentService;

    @Autowired
    private TicketEscalationApprovalService approvalService;

    private MockMvc mockMvc;

    @Autowired
    private ApprovalSecurityConfiguration security;

    @MockitoBean
    private JwtDecoder decoder;

    // jwt() bypasses decoding and the filter's converter; apply the real scope mapping explicitly.
    private JwtRequestPostProcessor token(String scope) {
        return jwt().jwt(jwt -> jwt.claim("scope", scope))
                .authorities(jwt -> security.approvalJwtAuthenticationConverter()
                        .convert(jwt).getAuthorities());
    }

    @BeforeEach
    void setUp() {
        reset(agentService, approvalService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void unauthenticatedCallerCannotCreateProposal() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"INC-1001\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(agentService, approvalService);
    }

    @Test
    void suppliedPrincipalAndUsernameHeaderDoNotAuthenticateCaller() throws Exception {
        mockMvc.perform(post(ENDPOINT + "/{id}/approve", UUID.randomUUID())
                        .principal(() -> "approver").header("X-Username", "approver"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(agentService, approvalService);
    }

    @Test
    void escalationPermissionDoesNotGrantReviewPermission() throws Exception {
        mockMvc.perform(post(ENDPOINT).with(token("ticket.escalate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"INC-1001\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agentService, approvalService);
    }

    @Test
    void reviewerCreatesProposalFromServerAnalysisWithoutCsrf() throws Exception {
        AgentResult analysis = new AgentResult("Requires attention", List.of(), true);
        EscalationProposal proposal = new EscalationProposal(
                UUID.randomUUID(), "INC-1001", "HIGH priority requires review"
        );
        when(agentService.run("INC-1001")).thenReturn(analysis);
        when(approvalService.propose("INC-1001", analysis)).thenReturn(Optional.of(proposal));

        var result = mockMvc.perform(post(ENDPOINT).with(token("ticket.review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"INC-1001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(proposal.id().toString())).andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();

        verify(agentService).run("INC-1001");
        verify(approvalService).propose("INC-1001", analysis);
        verifyNoMoreInteractions(agentService, approvalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void reviewerCannotConsumeProposal(String operation) throws Exception {
        mockMvc.perform(post(ENDPOINT + "/{id}/{operation}", UUID.randomUUID(), operation)
                        .with(token("ticket.review")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agentService, approvalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void escalatorConsumesProposalExactlyOnceWithoutCsrf(String operation) throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(ENDPOINT + "/{id}/{operation}", id, operation)
                        .with(token("ticket.escalate")))
                .andExpect(status().isNoContent());

        if (operation.equals("approve")) {
            verify(approvalService).approve(id);
        } else {
            verify(approvalService).reject(id);
        }
        verifyNoMoreInteractions(approvalService);
        verifyNoInteractions(agentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unrelated.scope", "TICKET_REVIEW"})
    void unrecognizedScopesCannotCreateProposal(String scope) throws Exception {
        mockMvc.perform(post(ENDPOINT).with(token(scope)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"INC-1001\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agentService, approvalService);
    }

    @Test
    void basicAuthenticationIsNotAFallback() throws Exception {
        mockMvc.perform(post(ENDPOINT + "/{id}/approve", UUID.randomUUID())
                        .with(httpBasic("approver", "password")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(agentService, approvalService);
    }

    @Test
    void sessionAuthenticationIsNotAFallback() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated("approver", "unused",
                        List.of(new SimpleGrantedAuthority("TICKET_ESCALATE")))));
        mockMvc.perform(post(ENDPOINT + "/{id}/approve", UUID.randomUUID()).session(session))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(agentService, approvalService);
    }

    @Test
    void invalidBearerTokenNeverReachesApprovalService() throws Exception {
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid signature"));
        mockMvc.perform(post(ENDPOINT + "/{id}/approve", UUID.randomUUID())
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(agentService, approvalService);
    }

    @Test
    void bearerFilterUsesProductionScopeConverter() throws Exception {
        Jwt decoded = Jwt.withTokenValue("verified-test-token").header("alg", "RS256")
                .subject("approver").claim("scope", "ticket.escalate").build();
        when(decoder.decode("verified-test-token")).thenReturn(decoded);
        UUID id = UUID.randomUUID();
        mockMvc.perform(post(ENDPOINT + "/{id}/approve", id)
                        .header("Authorization", "Bearer verified-test-token"))
                .andExpect(status().isNoContent());
        verify(decoder).decode("verified-test-token");
        verify(approvalService).approve(id);
        verifyNoMoreInteractions(approvalService);
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
