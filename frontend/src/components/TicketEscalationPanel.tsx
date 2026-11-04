import { useEffect, useId, useRef, useState } from "react";
import {
  approveEscalation,
  createEscalationProposal,
  rejectEscalation,
} from "../api/escalationApi";
import type { EscalationProposal } from "../types/EscalationProposal";

type Decision = "approved" | "rejected";
type ApprovalState =
  | { phase: "idle" }
  | { phase: "loading" }
  | { phase: "ineligible" }
  | { phase: "review"; proposal: EscalationProposal }
  | { phase: "submitting"; proposal: EscalationProposal }
  | { phase: "done"; decision: Decision }
  | { phase: "error"; message: string };

export default function TicketEscalationPanel({ ticketId }: { ticketId: string }) {
  // A new ticket gets a fresh session immediately, including when switching A → B → A.
  return <ApprovalSession key={ticketId} ticketId={ticketId} />;
}

function ApprovalSession({ ticketId }: { ticketId: string }) {
  const [state, setState] = useState<ApprovalState>({ phase: "idle" });
  const activeRequest = useRef<object | null>(null);
  const titleId = useId();
  const busy = state.phase === "loading" || state.phase === "submitting";

  useEffect(() => () => {
    // Ignore responses for a ticket that has changed or a panel that has unmounted.
    activeRequest.current = null;
  }, []);

  async function requestReview() {
    if (activeRequest.current || state.phase === "review") return;
    const id = ticketId.trim();
    if (!id) {
      setState({ phase: "error", message: "Please select a ticket ID." });
      return;
    }

    const request = {};
    activeRequest.current = request;
    setState({ phase: "loading" });
    try {
      const proposal = await createEscalationProposal(id);
      if (activeRequest.current !== request) return;
      setState(proposal ? { phase: "review", proposal } : { phase: "ineligible" });
    } catch (error) {
      if (activeRequest.current !== request) return;
      setState({ phase: "error", message: errorMessage(error) });
    } finally {
      if (activeRequest.current === request) activeRequest.current = null;
    }
  }

  async function decide(decision: Decision) {
    if (state.phase !== "review" || activeRequest.current) return;
    const { proposal } = state;
    const request = {};
    activeRequest.current = request;
    setState({ phase: "submitting", proposal });
    try {
      if (decision === "approved") await approveEscalation(proposal.id);
      else await rejectEscalation(proposal.id);
      if (activeRequest.current !== request) return;
      setState({ phase: "done", decision });
    } catch (error) {
      if (activeRequest.current !== request) return;
      // Remove decision controls after failure; never retry a consumed proposal automatically.
      setState({ phase: "error", message: errorMessage(error) });
    } finally {
      if (activeRequest.current === request) activeRequest.current = null;
    }
  }

  return (
    <section className="analyzer-card agent-card" aria-labelledby={titleId}>
      <header className="card-header">
        <div className="eyebrow">Human review</div>
        <h2 id={titleId}>Ticket escalation</h2>
        <p>Review an escalation proposal for ticket {ticketId || "—"}. Escalation requires your explicit approval.</p>
      </header>
      <div className="analysis-form">
        <button type="button" onClick={requestReview} disabled={busy || state.phase === "review"}>
          Request escalation review
        </button>
      </div>
      <div className="result-region" aria-live="polite" aria-busy={busy}>
        {state.phase === "idle" && <p>No escalation has been proposed.</p>}
        {state.phase === "loading" && <p>Requesting AI review…</p>}
        {state.phase === "ineligible" && <p>No escalation proposal is available for this ticket.</p>}
        {(state.phase === "review" || state.phase === "submitting") && (
          <article className="result-copy">
            <section>
              <h3>Proposed action: Escalate ticket</h3>
              <p>Ticket ID: {state.proposal.ticketId}</p>
              <p>{state.proposal.reason}</p>
            </section>
            <div className="analysis-form">
              <button type="button" disabled={busy} onClick={() => decide("approved")}>Approve</button>
              <button type="button" disabled={busy} onClick={() => decide("rejected")}>Reject</button>
            </div>
            {state.phase === "submitting" && <p>Submitting your decision…</p>}
          </article>
        )}
        {state.phase === "done" && <p>Escalation {state.decision}.</p>}
        {state.phase === "error" && <div className="error-state" role="alert">{state.message}</div>}
      </div>
    </section>
  );
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Unable to submit the escalation request.";
}
