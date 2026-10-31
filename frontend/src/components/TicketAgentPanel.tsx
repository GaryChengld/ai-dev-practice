import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { streamTicketAgent } from "../api/ticketApi";
import type { AgentAnalysis, AgentStatus, AgentStep } from "../types/AgentAnalysis";

type AgentUiState =
  | { phase: "idle" }
  | { phase: "loading"; steps: AgentStep[] }
  | { phase: "success"; data: AgentAnalysis }
  | { phase: "error"; message: string };

const statusPresentation: Record<
  AgentStatus,
  { tone: "success" | "warning" | "error"; label: string }
> = {
  COMPLETED: { tone: "success", label: "Analysis completed" },
  STEP_LIMIT_REACHED: { tone: "warning", label: "Step limit reached" },
  PLANNER_ERROR: { tone: "error", label: "Planning failed" },
  TOOL_ERROR: { tone: "error", label: "Tool execution failed" },
};

function TicketAgentPanel() {
  const [ticketId, setTicketId] = useState("");
  const [state, setState] = useState<AgentUiState>({ phase: "idle" });
  const activeRequest = useRef<AbortController | null>(null);
  const isLoading = state.phase === "loading";

  useEffect(() => {
    return () => {
      activeRequest.current?.abort();
      activeRequest.current = null;
    };
  }, []);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    activeRequest.current?.abort();
    activeRequest.current = null;

    const trimmedTicketId = ticketId.trim();
    if (!trimmedTicketId) {
      setState({ phase: "error", message: "Please enter a ticket ID." });
      return;
    }

    const controller = new AbortController();
    activeRequest.current = controller;
    setState({ phase: "loading", steps: [] });
    try {
      const data = await streamTicketAgent(trimmedTicketId, controller.signal, (step) => {
        if (activeRequest.current !== controller || controller.signal.aborted) {
          return;
        }
        setState((current) => {
          if (
            activeRequest.current !== controller ||
            controller.signal.aborted ||
            current.phase !== "loading"
          ) {
            return current;
          }
          return { phase: "loading", steps: [...current.steps, step] };
        });
      });
      if (activeRequest.current !== controller || controller.signal.aborted) {
        return;
      }
      setState({ phase: "success", data });
    } catch (error) {
      if (activeRequest.current !== controller || controller.signal.aborted) {
        return;
      }
      setState({
        phase: "error",
        message:
          error instanceof Error
            ? error.message
            : "Unable to reach the ticket agent. Please try again.",
      });
    } finally {
      if (activeRequest.current === controller) {
        activeRequest.current = null;
      }
    }
  };

  return (
    <section className="analyzer-card agent-card" aria-labelledby="agent-title">
      <header className="card-header">
        <div className="eyebrow">Ticket investigation</div>
        <h2 id="agent-title">AI Ticket Agent</h2>
        <p>
          Enter an existing ticket ID to check whether it needs immediate
          attention and review the agent's execution history.
        </p>
      </header>

      <form className="analysis-form" onSubmit={handleSubmit} noValidate>
        <label htmlFor="agent-ticket-id">Ticket ID</label>
        <input
          id="agent-ticket-id"
          name="ticketId"
          type="text"
          value={ticketId}
          onChange={(event) => {
            setTicketId(event.target.value);
            if (state.phase === "error") setState({ phase: "idle" });
          }}
          placeholder="INC-1001"
          required
          aria-describedby="agent-ticket-hint"
        />
        <div className="form-footer">
          <span id="agent-ticket-hint">Use a ticket ID, such as INC-1001.</span>
        </div>
        <button type="submit">
          {isLoading ? (
            <>
              <span className="spinner" aria-hidden="true" />
              Run AI Agent again
            </>
          ) : (
            "Run AI Agent"
          )}
        </button>
      </form>

      <div className="result-region" aria-live="polite" aria-busy={isLoading}>
        {state.phase === "idle" && (
          <div className="empty-state">The agent's results will appear here.</div>
        )}
        {state.phase === "loading" && (
          <div className="loading-state">
            <div className="empty-state">Analyzing the ticket. Please wait.</div>
            <AgentHistory steps={state.steps} />
          </div>
        )}
        {state.phase === "error" && (
          <div className="error-state" role="alert">
            <strong>Unable to run the agent</strong>
            <span>{state.message}</span>
          </div>
        )}
        {state.phase === "success" && <AgentResult analysis={state.data} />}
      </div>
    </section>
  );
}

function AgentResult({ analysis }: { analysis: AgentAnalysis }) {
  const presentation = statusPresentation[analysis.status];

  return (
    <article className="analysis-result agent-result">
      <div className="result-heading">
        <h3>{presentation.label}</h3>
        <span className={`agent-status agent-status-${presentation.tone}`}>
          Status: {analysis.status}
        </span>
      </div>
      <div className="result-copy">
        <section>
          <h3>Answer</h3>
          <p>{analysis.answer}</p>
        </section>
        <AgentHistory steps={analysis.steps} />
      </div>
    </article>
  );
}

function AgentHistory({ steps }: { steps: AgentStep[] }) {
  return (
    <section>
      <h3>Execution history</h3>
      <p>
        {steps.length} successful tool execution{steps.length === 1 ? "" : "s"}
      </p>
      {steps.length > 0 ? (
        <ol className="agent-steps">
          {steps.map((step, index) => (
            <li key={index}>
              <strong>{step.action}</strong>
              <span>{step.result}</span>
            </li>
          ))}
        </ol>
      ) : (
        <p>No tools completed.</p>
      )}
    </section>
  );
}

export default TicketAgentPanel;
