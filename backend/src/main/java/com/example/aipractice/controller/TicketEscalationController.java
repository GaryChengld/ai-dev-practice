package com.example.aipractice.controller;

import com.example.aipractice.dto.ApiError;
import com.example.aipractice.exception.ProposalNotPendingException;
import com.example.aipractice.service.ApprovalAuthorizer;
import com.example.aipractice.service.TicketAgentService;
import com.example.aipractice.service.TicketEscalationApprovalService;
import com.example.aipractice.service.TicketEscalationApprovalService.EscalationProposal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.UUID;

/**
 * Development-only HTTP boundary secured by ApprovalSecurityConfiguration.
 * The approval service and gateway must still be supplied separately.
 */
@RestController
@RequestMapping("/api/agents/tickets/escalation-proposals")
@Profile("approval-demo")
public class TicketEscalationController {

    private final TicketAgentService agentService;
    private final TicketEscalationApprovalService approvalService;
    private final ApprovalAuthorizer authorizer;

    public TicketEscalationController(
            TicketAgentService agentService,
            TicketEscalationApprovalService approvalService,
            ApprovalAuthorizer authorizer
    ) {
        this.agentService = agentService;
        this.approvalService = approvalService;
        this.authorizer = authorizer;
    }

    public record ProposalRequest(@NotBlank String ticketId) {}

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EscalationProposal> propose(
            @Valid @RequestBody ProposalRequest request
    ) {
        TicketAgentService.AgentResult analysis = agentService.run(request.ticketId());
        return approvalService.propose(request.ticketId(), analysis)
                .map(proposal -> ResponseEntity.status(HttpStatus.CREATED).body(proposal))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id, Principal principal) {
        requireAuthorization(principal);
        approvalService.approve(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id, Principal principal) {
        requireAuthorization(principal);
        approvalService.reject(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAuthorization(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        authorizer.requireCanEscalate(principal);
    }

    @ExceptionHandler(ProposalNotPendingException.class)
    public ResponseEntity<ApiError> handleProposalNotPending(ProposalNotPendingException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("PROPOSAL_NOT_PENDING", exception.getMessage()));
    }
}
