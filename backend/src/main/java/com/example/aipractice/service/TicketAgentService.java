package com.example.aipractice.service;

import com.example.aipractice.tools.TicketPriorityTools;
import com.example.aipractice.tools.TicketTools;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Executes planner decisions with bounded tool access, independently of the chat pipeline.
 */
public class TicketAgentService {

    public enum Action {
        CHECK_STATUS,
        CHECK_PRIORITY,
        FINISH
    }

    public enum AgentStatus {
        COMPLETED,
        STEP_LIMIT_REACHED,
        PLANNER_ERROR,
        TOOL_ERROR
    }

    public record AgentDecision(Action action, @Schema(nullable = true) String finalAnswer) {}

    public record AgentStep(Action action, String result) {}

    public record AgentResult(String answer, List<AgentStep> steps, boolean completed, AgentStatus status) {
        public AgentResult(String answer, List<AgentStep> steps, boolean completed) {
            this(answer, steps, completed,
                    completed ? AgentStatus.COMPLETED : AgentStatus.STEP_LIMIT_REACHED);
        }
    }

    @FunctionalInterface
    public interface AgentPlanner {
        AgentDecision decide(String ticketId, List<AgentStep> previousSteps);
    }

    private final TicketTools ticketTools;
    private final TicketPriorityTools priorityTools;
    private final AgentPlanner planner;
    private final int maxSteps;

    public TicketAgentService(
            TicketTools ticketTools,
            TicketPriorityTools priorityTools,
            AgentPlanner planner,
            int maxSteps
    ) {
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps must be greater than zero");
        }
        this.ticketTools = Objects.requireNonNull(ticketTools, "ticketTools cannot be null");
        this.priorityTools = Objects.requireNonNull(priorityTools, "priorityTools cannot be null");
        this.planner = Objects.requireNonNull(planner, "planner cannot be null");
        this.maxSteps = maxSteps;
    }

    /**
     * Runs at most {@code maxSteps} planning decisions, including FINISH.
     * Only successful tool executions appear in the immutable result trace.
     * Planner and tool failures return a classified result with previously completed steps.
     *
     * @throws IllegalArgumentException when the ticket ID is null or blank
     * @throws IllegalStateException when the planner returns an invalid decision,
     *         or priority is requested before the latest status check has returned
     *         OPEN or IN_PROGRESS
     */
    public AgentResult run(String ticketId) {
        return run(ticketId, step -> {});
    }

    /**
     * Reports each successful tool execution synchronously after recording it in history.
     * The callback is trusted code and must not throw; callback failures are not tool failures.
     *
     * @throws NullPointerException when the progress callback is null
     */
    public AgentResult run(String ticketId, Consumer<AgentStep> onStep) {
        if (ticketId == null || ticketId.isBlank()) {
            throw new IllegalArgumentException("ticketId cannot be null or blank");
        }
        Objects.requireNonNull(onStep, "onStep cannot be null");

        List<AgentStep> steps = new ArrayList<>();
        String latestStatus = null;

        for (int iteration = 0; iteration < maxSteps; iteration++) {
            AgentDecision decision;
            try {
                decision = planner.decide(ticketId, List.copyOf(steps));
            } catch (RuntimeException ex) {
                return new AgentResult(
                        "Unable to complete ticket analysis",
                        List.copyOf(steps),
                        false,
                        AgentStatus.PLANNER_ERROR
                );
            }
            validateDecision(decision);

            switch (decision.action()) {
                case CHECK_STATUS -> {
                    try {
                        latestStatus = ticketTools.getTicketStatus(ticketId);
                    } catch (RuntimeException ex) {
                        return new AgentResult(
                                "Unable to retrieve ticket status",
                                List.copyOf(steps),
                                false,
                                AgentStatus.TOOL_ERROR
                        );
                    }
                    AgentStep step = new AgentStep(Action.CHECK_STATUS, latestStatus);
                    steps.add(step);
                    onStep.accept(step);
                }
                case CHECK_PRIORITY -> {
                    if (!"OPEN".equals(latestStatus) && !"IN_PROGRESS".equals(latestStatus)) {
                        throw new IllegalStateException(
                                "Priority requires a previous status check returning OPEN or IN_PROGRESS"
                        );
                    }
                    String priority;
                    try {
                        priority = priorityTools.getTicketPriority(ticketId);
                    } catch (RuntimeException ex) {
                        return new AgentResult(
                                "Unable to retrieve ticket priority",
                                List.copyOf(steps),
                                false,
                                AgentStatus.TOOL_ERROR
                        );
                    }
                    AgentStep step = new AgentStep(Action.CHECK_PRIORITY, priority);
                    steps.add(step);
                    onStep.accept(step);
                }
                case FINISH -> {
                    return new AgentResult(decision.finalAnswer(), List.copyOf(steps), true);
                }
            }
        }

        return new AgentResult(
                "Agent stopped: maximum step count reached",
                List.copyOf(steps),
                false
        );
    }

    private void validateDecision(AgentDecision decision) {
        if (decision == null || decision.action() == null) {
            throw new IllegalStateException("Agent planner returned an invalid decision");
        }

        if (decision.action() == Action.FINISH
                && (decision.finalAnswer() == null || decision.finalAnswer().isBlank())) {
            throw new IllegalStateException("FINISH requires a nonblank final answer");
        }
    }
}
