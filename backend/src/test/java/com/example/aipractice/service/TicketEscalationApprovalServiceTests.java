package com.example.aipractice.service;

import com.example.aipractice.service.TicketAgentService.Action;
import com.example.aipractice.service.TicketAgentService.AgentResult;
import com.example.aipractice.service.TicketAgentService.AgentStatus;
import com.example.aipractice.service.TicketAgentService.AgentStep;
import com.example.aipractice.service.TicketEscalationApprovalService.EscalationProposal;
import com.example.aipractice.service.TicketEscalationApprovalService.TicketEscalationGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class TicketEscalationApprovalServiceTests {

    private final TicketEscalationGateway gateway = mock(TicketEscalationGateway.class);
    private final TicketEscalationApprovalService service = new TicketEscalationApprovalService(gateway);

    @Test
    void proposesEscalationForCompletedHighPriorityAnalysisWithoutExecuting() {
        EscalationProposal proposal = proposeHighPriority("INC-1001");

        assertThat(proposal.id()).isNotNull();
        assertThat(proposal.ticketId()).isEqualTo("INC-1001");
        assertThat(proposal.reason()).contains("HIGH");
        verifyNoInteractions(gateway);
    }

    @Test
    void doesNotProposeForIncompleteOrFailedAnalysisEvenWithHighPriority() {
        for (AgentStatus status : AgentStatus.values()) {
            assertThat(service.propose("INC-1001", new AgentResult(
                    "Not completed", List.of(priority("HIGH")), false, status
            ))).isEmpty();
            if (status != AgentStatus.COMPLETED) {
                assertThat(service.propose("INC-1001", new AgentResult(
                        "Inconsistent result", List.of(priority("HIGH")), true, status
                ))).isEmpty();
            }
        }
        verifyNoInteractions(gateway);
    }

    @Test
    void doesNotProposeWithoutHighPriorityEvidence() {
        for (String priority : List.of("LOW", "MEDIUM", "NOT_FOUND")) {
            assertThat(service.propose("INC-1001", completed(priority(priority)))).isEmpty();
        }
        assertThat(service.propose("INC-1001", completed())).isEmpty();
        assertThat(service.propose("INC-1001",
                completed(new AgentStep(Action.CHECK_STATUS, "HIGH")))).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void usesTheMostRecentPriorityCheck() {
        assertThat(service.propose("INC-1001",
                completed(priority("HIGH"), priority("LOW")))).isEmpty();
        assertThat(service.propose("INC-1001", completed(
                priority("LOW"), priority("HIGH"), new AgentStep(Action.CHECK_STATUS, "OPEN")
        ))).isPresent();
        verifyNoInteractions(gateway);
    }

    @Test
    void approvesTheStoredTicketExactlyOnce() {
        EscalationProposal proposal = proposeHighPriority("INC-1001");

        service.approve(proposal.id());

        verify(gateway).escalate("INC-1001");
        assertNoLongerPending(proposal.id());
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void rejectsPendingProposalWithoutExecutingAndPreventsLaterApproval() {
        EscalationProposal proposal = proposeHighPriority("INC-1001");

        service.reject(proposal.id());

        assertNoLongerPending(proposal.id());
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsUnknownProposalIdsWithoutExecuting() {
        assertNoLongerPending(UUID.randomUUID());
        verifyNoInteractions(gateway);
    }

    @Test
    void generatesIndependentIdsAndApprovesOnlyTheSelectedStoredTicket() {
        EscalationProposal first = proposeHighPriority("INC-1001");
        EscalationProposal second = proposeHighPriority("INC-1002");
        assertThat(second.id()).isNotEqualTo(first.id());

        service.approve(second.id());
        service.reject(first.id());

        verify(gateway).escalate("INC-1002");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void doesNotRetryAnApprovalWhenTheGatewayFails() {
        EscalationProposal proposal = proposeHighPriority("INC-1001");
        IllegalStateException failure = new IllegalStateException("Ticket system unavailable");
        doThrow(failure).when(gateway).escalate("INC-1001");

        assertThatThrownBy(() -> service.approve(proposal.id())).isSameAs(failure);

        assertNoLongerPending(proposal.id());
        verify(gateway).escalate("INC-1001");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void concurrentApprovalsAllowOnlyOneExecutionAttempt() throws Exception {
        EscalationProposal proposal = proposeHighPriority("INC-1001");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> approveAfterStart(proposal.id(), ready, start));
            Future<Boolean> second = executor.submit(() -> approveAfterStart(proposal.id(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            verify(gateway).escalate("INC-1001");
            verifyNoMoreInteractions(gateway);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rejectsNullAndBlankTicketIdsWithoutExecuting() {
        for (String ticketId : new String[] {null, "", " \t\n"}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> service.propose(ticketId, completed(priority("HIGH"))))
                    .withMessage("ticketId cannot be null or blank");
        }
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsNullAnalysisAndProposalIdsWithoutExecuting() {
        assertThatThrownBy(() -> service.propose("INC-1001", null))
                .isInstanceOf(NullPointerException.class).hasMessage("analysis cannot be null");
        assertThatThrownBy(() -> service.approve(null))
                .isInstanceOf(NullPointerException.class).hasMessage("proposalId cannot be null");
        assertThatThrownBy(() -> service.reject(null))
                .isInstanceOf(NullPointerException.class).hasMessage("proposalId cannot be null");
        verifyNoInteractions(gateway);
    }

    private EscalationProposal proposeHighPriority(String ticketId) {
        return service.propose(ticketId, completed(
                new AgentStep(Action.CHECK_STATUS, "OPEN"), priority("HIGH")
        )).orElseThrow();
    }

    private AgentResult completed(AgentStep... steps) {
        return new AgentResult("Ticket analysis complete", List.of(steps), true, AgentStatus.COMPLETED);
    }

    private AgentStep priority(String value) {
        return new AgentStep(Action.CHECK_PRIORITY, value);
    }

    private void assertNoLongerPending(UUID id) {
        assertThatIllegalStateException().isThrownBy(() -> service.approve(id))
                .withMessage("Proposal is no longer pending");
        assertThatIllegalStateException().isThrownBy(() -> service.reject(id))
                .withMessage("Proposal is no longer pending");
    }

    private boolean approveAfterStart(UUID id, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Approval start was not released");
        }
        try {
            service.approve(id);
            return true;
        } catch (IllegalStateException ex) {
            assertThat(ex).hasMessage("Proposal is no longer pending");
            return false;
        }
    }
}
