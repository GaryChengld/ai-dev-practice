package com.example.aipractice.service;

import com.example.aipractice.exception.ProposalNotPendingException;
import com.example.aipractice.service.TicketAgentService.Action;
import com.example.aipractice.service.TicketAgentService.AgentResult;
import com.example.aipractice.service.TicketAgentService.AgentStatus;
import com.example.aipractice.service.TicketAgentService.AgentStep;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds escalation proposals in memory until explicitly approved or rejected.
 * Consumption permits at most one gateway execution attempt per proposal in this instance.
 */
public class TicketEscalationApprovalService {

    public record EscalationProposal(UUID id, String ticketId, String reason) {}

    @FunctionalInterface
    public interface TicketEscalationGateway {
        void escalate(String ticketId);
    }

    private final TicketEscalationGateway gateway;
    private final ConcurrentHashMap<UUID, EscalationProposal> pending = new ConcurrentHashMap<>();

    public TicketEscalationApprovalService(TicketEscalationGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway cannot be null");
    }

    public Optional<EscalationProposal> propose(String ticketId, AgentResult analysis) {
        if (ticketId == null || ticketId.isBlank()) {
            throw new IllegalArgumentException("ticketId cannot be null or blank");
        }
        Objects.requireNonNull(analysis, "analysis cannot be null");

        if (!analysis.completed() || analysis.status() != AgentStatus.COMPLETED) {
            return Optional.empty();
        }

        List<AgentStep> steps = analysis.steps();
        for (int index = steps.size() - 1; index >= 0; index--) {
            AgentStep step = steps.get(index);
            if (step.action() == Action.CHECK_PRIORITY) {
                if (!"HIGH".equals(step.result())) {
                    return Optional.empty();
                }
                EscalationProposal proposal = new EscalationProposal(
                        UUID.randomUUID(), ticketId,
                        "Agent analysis completed with HIGH ticket priority."
                );
                pending.put(proposal.id(), proposal);
                return Optional.of(proposal);
            }
        }
        return Optional.empty();
    }

    /**
     * Consumes approval before attempting the update. Gateway failures propagate and
     * leave the proposal consumed; retrying approval cannot repeat the external action.
     * Any caller authorization must be checked before calling this method.
     */
    public void approve(UUID proposalId) {
        EscalationProposal proposal = consume(proposalId);
        gateway.escalate(proposal.ticketId());
    }

    public void reject(UUID proposalId) {
        consume(proposalId);
    }

    private EscalationProposal consume(UUID proposalId) {
        Objects.requireNonNull(proposalId, "proposalId cannot be null");
        EscalationProposal proposal = pending.remove(proposalId);
        if (proposal == null) {
            throw new ProposalNotPendingException();
        }
        return proposal;
    }
}
