package com.example.aipractice.service;

import com.example.aipractice.service.TicketAgentService.Action;
import com.example.aipractice.service.TicketAgentService.AgentDecision;
import com.example.aipractice.service.TicketAgentService.AgentPlanner;
import com.example.aipractice.service.TicketAgentService.AgentResult;
import com.example.aipractice.service.TicketAgentService.AgentStep;
import com.example.aipractice.service.TicketAgentService.AgentStatus;
import com.example.aipractice.tools.TicketPriorityTools;
import com.example.aipractice.tools.TicketTools;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TicketAgentServiceTests {

    private final TicketService ticketService = new TicketService();
    private final TicketTools ticketTools = spy(new TicketTools(ticketService));
    private final TicketPriorityTools priorityTools = spy(new TicketPriorityTools(ticketService));

    @Test
    void emitsProgressForEachSuccessfulToolExecution() {
        List<AgentStep> events = new ArrayList<>();

        AgentResult result = agent(attentionPlanner(), 3).run("INC-1001", events::add);

        assertThat(events).containsExactly(
                new AgentStep(Action.CHECK_STATUS, "OPEN"),
                new AgentStep(Action.CHECK_PRIORITY, "HIGH")
        );
        assertThat(result.steps()).containsExactlyElementsOf(events);
        assertThat(result.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThatThrownBy(() -> result.steps().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emitsOnlySuccessfulProgressWhenPriorityLookupFails() {
        List<AgentStep> events = new ArrayList<>();
        doThrow(new IllegalStateException("Ticket service unavailable"))
                .when(priorityTools).getTicketPriority("INC-1001");

        AgentResult result = agent(attentionPlanner(), 3).run("INC-1001", events::add);

        assertThat(events).containsExactly(new AgentStep(Action.CHECK_STATUS, "OPEN"));
        assertThat(result.steps()).containsExactlyElementsOf(events);
        assertThat(result.status()).isEqualTo(AgentStatus.TOOL_ERROR);
        assertThat(result.completed()).isFalse();
    }

    @Test
    void emitsNoProgressWhenPlanningFailsBeforeToolExecution() {
        List<AgentStep> events = new ArrayList<>();
        AgentPlanner failingPlanner = (ticketId, steps) -> {
            throw new IllegalStateException("Malformed model response");
        };

        AgentResult result = agent(failingPlanner, 3).run("INC-1001", events::add);

        assertThat(events).isEmpty();
        assertThat(result.steps()).isEmpty();
        assertThat(result.status()).isEqualTo(AgentStatus.PLANNER_ERROR);
        assertThat(result.completed()).isFalse();
        verifyNoInteractions(ticketTools, priorityTools);
    }

    @Test
    void preservesEmittedProgressWhenLaterPlanningFails() {
        List<AgentStep> events = new ArrayList<>();
        AgentPlanner failingPlanner = (ticketId, steps) -> {
            if (steps.isEmpty()) {
                return new AgentDecision(Action.CHECK_STATUS, null);
            }
            throw new IllegalStateException("Model service unavailable");
        };

        AgentResult result = agent(failingPlanner, 3).run("INC-1001", events::add);

        assertThat(events).containsExactly(new AgentStep(Action.CHECK_STATUS, "OPEN"));
        assertThat(result.steps()).containsExactlyElementsOf(events);
        assertThat(result.status()).isEqualTo(AgentStatus.PLANNER_ERROR);
        assertThat(result.completed()).isFalse();
        verifyNoInteractions(priorityTools);
    }

    @Test
    void deliversProgressBeforeTheNextPlanningDecision() {
        List<AgentStep> events = new ArrayList<>();
        AgentPlanner planner = (ticketId, steps) -> {
            assertThat(events).containsExactlyElementsOf(steps);
            return attentionPlanner().decide(ticketId, steps);
        };

        AgentResult result = agent(planner, 3).run("INC-1001", events::add);

        assertThat(result.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(events).hasSize(2);
    }

    @Test
    void rejectsNullProgressCallbacksBeforePlanning() {
        AgentPlanner planner = mock(AgentPlanner.class);

        assertThatThrownBy(() -> agent(planner, 3).run("INC-1001", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("onStep cannot be null");

        verifyNoInteractions(planner, ticketTools, priorityTools);
    }

    @Test
    void completesMultiStepExecutionUsingPreviousResults() {
        AgentResult result = agent(attentionPlanner(), 3).run("INC-1001");

        assertThat(result.completed()).isTrue();
        assertThat(result.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("Ticket requires immediate attention");
        assertThat(result.steps()).containsExactly(
                new AgentStep(Action.CHECK_STATUS, "OPEN"),
                new AgentStep(Action.CHECK_PRIORITY, "HIGH")
        );
    }

    @Test
    void stopsRepeatedActionsAtTheStepLimit() {
        List<List<AgentStep>> observedSteps = new ArrayList<>();
        AgentPlanner planner = (ticketId, steps) -> {
            observedSteps.add(steps);
            return new AgentDecision(Action.CHECK_STATUS, null);
        };

        AgentResult result = agent(planner, 2).run("INC-1001");

        assertThat(result.completed()).isFalse();
        assertThat(result.status()).isEqualTo(AgentStatus.STEP_LIMIT_REACHED);
        assertThat(result.answer()).isEqualTo("Agent stopped: maximum step count reached");
        assertThat(result.steps()).containsExactly(
                new AgentStep(Action.CHECK_STATUS, "OPEN"),
                new AgentStep(Action.CHECK_STATUS, "OPEN")
        );
        assertThat(observedSteps).hasSize(2);
    }

    @Test
    void returnsPlannerErrorWhenPlanningFails() {
        AgentPlanner failingPlanner = (ticketId, steps) -> {
            throw new IllegalStateException("Malformed model response");
        };

        AgentResult result = agent(failingPlanner, 3).run("INC-1001");

        assertThat(result.completed()).isFalse();
        assertThat(result.status()).isEqualTo(AgentStatus.PLANNER_ERROR);
        assertThat(result.answer()).isEqualTo("Unable to complete ticket analysis");
        assertThat(result.steps()).isEmpty();
        verifyNoInteractions(ticketTools, priorityTools);
    }

    @Test
    void preservesSuccessfulStepsWhenLaterPlanningFails() {
        AgentPlanner failingPlanner = (ticketId, steps) -> {
            if (steps.isEmpty()) {
                return new AgentDecision(Action.CHECK_STATUS, null);
            }
            throw new IllegalStateException("Model service unavailable");
        };

        AgentResult result = agent(failingPlanner, 3).run("INC-1001");

        assertThat(result.completed()).isFalse();
        assertThat(result.status()).isEqualTo(AgentStatus.PLANNER_ERROR);
        assertThat(result.answer()).isEqualTo("Unable to complete ticket analysis");
        assertThat(result.steps()).containsExactly(new AgentStep(Action.CHECK_STATUS, "OPEN"));
        assertThatThrownBy(() -> result.steps().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        verify(ticketTools).getTicketStatus("INC-1001");
        verifyNoMoreInteractions(ticketTools);
        verifyNoInteractions(priorityTools);
    }

    @Test
    void returnsToolErrorWhenStatusLookupFails() {
        TicketTools failingTools = mock(TicketTools.class);
        when(failingTools.getTicketStatus("INC-1001"))
                .thenThrow(new IllegalStateException("Ticket service unavailable"));
        TicketAgentService agent = new TicketAgentService(
                failingTools,
                priorityTools,
                (ticketId, steps) -> new AgentDecision(Action.CHECK_STATUS, null),
                3
        );

        AgentResult result = agent.run("INC-1001");

        assertThat(result.completed()).isFalse();
        assertThat(result.status()).isEqualTo(AgentStatus.TOOL_ERROR);
        assertThat(result.answer()).isEqualTo("Unable to retrieve ticket status");
        assertThat(result.steps()).isEmpty();
        verify(failingTools).getTicketStatus("INC-1001");
        verifyNoMoreInteractions(failingTools);
        verifyNoInteractions(priorityTools);
    }

    @Test
    void preservesSuccessfulStatusWhenPriorityLookupFails() {
        doThrow(new IllegalStateException("Ticket service unavailable"))
                .when(priorityTools).getTicketPriority("INC-1001");

        AgentResult result = agent(attentionPlanner(), 3).run("INC-1001");

        assertThat(result.completed()).isFalse();
        assertThat(result.status()).isEqualTo(AgentStatus.TOOL_ERROR);
        assertThat(result.answer()).isEqualTo("Unable to retrieve ticket priority");
        assertThat(result.steps()).containsExactly(new AgentStep(Action.CHECK_STATUS, "OPEN"));
        assertThatThrownBy(() -> result.steps().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        verify(ticketTools).getTicketStatus("INC-1001");
        verify(priorityTools).getTicketPriority("INC-1001");
        verifyNoMoreInteractions(ticketTools, priorityTools);
    }

    @Test
    void rejectsPriorityBeforeStatusWithoutExecutingThePriorityTool() {
        AgentPlanner planner = (ticketId, steps) -> new AgentDecision(Action.CHECK_PRIORITY, null);

        // Invalid action ordering is a caller error, not a completed agent result.
        assertThatIllegalStateException()
                .isThrownBy(() -> agent(planner, 3).run("INC-1001"))
                .withMessage("Priority requires a previous status check returning OPEN or IN_PROGRESS");
        verifyNoInteractions(priorityTools);
    }

    @Test
    void rejectsPriorityForResolvedAndUnknownTickets() {
        AgentPlanner planner = (ticketId, steps) -> new AgentDecision(
                steps.isEmpty() ? Action.CHECK_STATUS : Action.CHECK_PRIORITY, null
        );
        TicketAgentService agent = agent(planner, 3);

        assertThatIllegalStateException().isThrownBy(() -> agent.run("INC-1003"));
        assertThatIllegalStateException().isThrownBy(() -> agent.run("INC-9999"));
        verifyNoInteractions(priorityTools);
    }

    @Test
    void allowsPriorityAfterAnInProgressStatus() {
        AgentResult result = agent(attentionPlanner(), 3).run("INC-1002");

        assertThat(result.completed()).isTrue();
        assertThat(result.answer()).isEqualTo("Ticket does not require immediate attention");
        assertThat(result.steps()).containsExactly(
                new AgentStep(Action.CHECK_STATUS, "IN_PROGRESS"),
                new AgentStep(Action.CHECK_PRIORITY, "MEDIUM")
        );
    }

    @Test
    void finishesResolvedTicketsWithoutCheckingPriority() {
        AgentResult result = agent(attentionPlanner(), 3).run("INC-1003");

        assertThat(result.completed()).isTrue();
        assertThat(result.answer()).isEqualTo("Ticket does not require immediate attention");
        assertThat(result.steps()).containsExactly(new AgentStep(Action.CHECK_STATUS, "RESOLVED"));
        verifyNoInteractions(priorityTools);
    }

    @Test
    void requiresRemainingBudgetForTheFinishDecision() {
        AgentResult result = agent(attentionPlanner(), 2).run("INC-1001");

        assertThat(result.completed()).isFalse();
        assertThat(result.steps()).hasSize(2);
        assertThat(result.answer()).isEqualTo("Agent stopped: maximum step count reached");
    }

    @Test
    void canFinishOnTheOnlyAllowedDecisionWithoutCallingTools() {
        TicketTools unusedTicketTools = mock(TicketTools.class);
        TicketAgentService agent = new TicketAgentService(
                unusedTicketTools,
                priorityTools,
                (ticketId, steps) -> new AgentDecision(Action.FINISH, "Already complete"),
                1
        );

        AgentResult result = agent.run("INC-1001");

        assertThat(result.completed()).isTrue();
        assertThat(result.answer()).isEqualTo("Already complete");
        assertThat(result.steps()).isEmpty();
        verifyNoInteractions(unusedTicketTools, priorityTools);
    }

    @Test
    void suppliesImmutableHistorySnapshotsAndReturnsAnImmutableTrace() {
        List<List<AgentStep>> snapshots = new ArrayList<>();
        AgentPlanner planner = (ticketId, steps) -> {
            assertThat(ticketId).isEqualTo("INC-1001");
            snapshots.add(steps);
            assertThatThrownBy(() -> steps.add(new AgentStep(Action.CHECK_STATUS, "FAKE")))
                    .isInstanceOf(UnsupportedOperationException.class);
            return attentionPlanner().decide(ticketId, steps);
        };

        AgentResult result = agent(planner, 3).run("INC-1001");

        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get(0)).isEmpty();
        assertThat(snapshots.get(1)).containsExactly(new AgentStep(Action.CHECK_STATUS, "OPEN"));
        assertThat(snapshots.get(2)).containsExactlyElementsOf(result.steps());
        assertThatThrownBy(() -> result.steps().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void usesTheLatestStatusWhenEnforcingPriorityAccess() {
        TicketTools changingStatusTools = spy(new TicketTools(ticketService));
        doReturn("OPEN", "RESOLVED").when(changingStatusTools).getTicketStatus("INC-1001");
        TicketAgentService agent = new TicketAgentService(
                changingStatusTools,
                priorityTools,
                (ticketId, steps) -> new AgentDecision(
                        steps.size() < 2 ? Action.CHECK_STATUS : Action.CHECK_PRIORITY, null
                ),
                3
        );

        assertThatIllegalStateException().isThrownBy(() -> agent.run("INC-1001"));
        verifyNoInteractions(priorityTools);
    }

    @Test
    void startsEachRunWithFreshState() {
        TicketAgentService agent = agent(attentionPlanner(), 3);

        assertThat(agent.run("INC-1001").steps()).hasSize(2);
        assertThat(agent.run("INC-1003").steps())
                .containsExactly(new AgentStep(Action.CHECK_STATUS, "RESOLVED"));
    }

    @Test
    void rejectsNullAndBlankTicketIdsBeforePlanning() {
        AgentPlanner planner = mock(AgentPlanner.class);
        TicketAgentService agent = agent(planner, 3);

        for (String ticketId : new String[] {null, "", " \t\n"}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> agent.run(ticketId))
                    .withMessage("ticketId cannot be null or blank");
        }
        verifyNoInteractions(planner, priorityTools);
    }

    @Test
    void rejectsNonPositiveStepLimits() {
        for (int maxSteps : new int[] {0, -1}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> agent(attentionPlanner(), maxSteps))
                    .withMessage("maxSteps must be greater than zero");
        }
    }

    @Test
    void rejectsNullPlannerDecisionsWithoutExecutingTools() {
        AgentPlanner planner = (ticketId, steps) -> null;

        assertThatIllegalStateException()
                .isThrownBy(() -> agent(planner, 3).run("INC-1001"))
                .withMessage("Agent planner returned an invalid decision");
        verifyNoInteractions(ticketTools, priorityTools);
    }

    @Test
    void rejectsDecisionsWithoutAnActionWithoutExecutingTools() {
        AgentPlanner planner = (ticketId, steps) -> new AgentDecision(null, null);

        assertThatIllegalStateException()
                .isThrownBy(() -> agent(planner, 3).run("INC-1001"))
                .withMessage("Agent planner returned an invalid decision");
        verifyNoInteractions(ticketTools, priorityTools);
    }

    @Test
    void rejectsFinishWithoutANonblankAnswerWithoutExecutingTools() {
        for (String finalAnswer : new String[] {null, "", " \t\n"}) {
            AgentPlanner planner = (ticketId, steps) -> new AgentDecision(Action.FINISH, finalAnswer);

            assertThatIllegalStateException()
                    .isThrownBy(() -> agent(planner, 3).run("INC-1001"))
                    .withMessage("FINISH requires a nonblank final answer");
        }
        verifyNoInteractions(ticketTools, priorityTools);
    }

    private TicketAgentService agent(AgentPlanner planner, int maxSteps) {
        return new TicketAgentService(ticketTools, priorityTools, planner, maxSteps);
    }

    private AgentPlanner attentionPlanner() {
        return (ticketId, steps) -> {
            if (steps.isEmpty()) {
                return new AgentDecision(Action.CHECK_STATUS, null);
            }
            AgentStep latestStep = steps.get(steps.size() - 1);
            if (latestStep.action() == Action.CHECK_STATUS
                    && ("OPEN".equals(latestStep.result()) || "IN_PROGRESS".equals(latestStep.result()))) {
                return new AgentDecision(Action.CHECK_PRIORITY, null);
            }
            String answer = latestStep.action() == Action.CHECK_PRIORITY && "HIGH".equals(latestStep.result())
                    ? "Ticket requires immediate attention"
                    : "Ticket does not require immediate attention";
            return new AgentDecision(Action.FINISH, answer);
        };
    }
}
