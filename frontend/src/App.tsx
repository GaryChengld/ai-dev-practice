import { FormEvent, useState } from "react";
import { analyzeTicket } from "./api/ticketApi";
import type { Priority, TicketAnalysis } from "./types/TicketAnalysis";

type AnalysisState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "success"; data: TicketAnalysis }
  | { status: "error"; message: string };

const priorityLabels: Record<Priority, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  CRITICAL: "Critical",
};

function App() {
  const [message, setMessage] = useState("");
  const [analysis, setAnalysis] = useState<AnalysisState>({ status: "idle" });

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const trimmedMessage = message.trim();
    if (!trimmedMessage) {
      setAnalysis({
        status: "error",
        message: "Please describe the support issue before analyzing it.",
      });
      return;
    }

    setAnalysis({ status: "loading" });

    try {
      const data = await analyzeTicket(trimmedMessage);
      setAnalysis({ status: "success", data });
    } catch (error) {
      setAnalysis({
        status: "error",
        message:
          error instanceof Error
            ? error.message
            : "Unable to analyze the ticket. Please try again.",
      });
    }
  };

  const isLoading = analysis.status === "loading";

  return (
    <main className="app-shell">
      <section className="analyzer-card" aria-labelledby="page-title">
        <header className="card-header">
          <div className="eyebrow">Support intelligence</div>
          <h1 id="page-title">AI Support Ticket Analyzer</h1>
          <p>
            Describe an issue to receive an instant category, priority, and
            suggested next step.
          </p>
        </header>

        <form className="analysis-form" onSubmit={handleSubmit} noValidate>
          <label htmlFor="ticket-message">Support issue</label>
          <textarea
            id="ticket-message"
            name="message"
            value={message}
            onChange={(event) => {
              setMessage(event.target.value);
              if (analysis.status === "error") {
                setAnalysis({ status: "idle" });
              }
            }}
            placeholder="Describe your problem..."
            rows={7}
            required
            disabled={isLoading}
            aria-describedby="message-hint"
          />
          <div className="form-footer">
            <span id="message-hint">Include symptoms and recent changes.</span>
            <span>{message.length} characters</span>
          </div>

          <button type="submit" disabled={isLoading}>
            {isLoading ? (
              <>
                <span className="spinner" aria-hidden="true" />
                Analyzing…
              </>
            ) : (
              "Analyze ticket"
            )}
          </button>
        </form>

        <div className="result-region" aria-live="polite">
          {analysis.status === "idle" && (
            <div className="empty-state">
              <span aria-hidden="true">↳</span>
              Your analysis will appear here.
            </div>
          )}

          {analysis.status === "loading" && (
            <div className="loading-state">
              <div className="skeleton skeleton-short" />
              <div className="skeleton" />
              <div className="skeleton skeleton-medium" />
            </div>
          )}

          {analysis.status === "error" && (
            <div className="error-state" role="alert">
              <strong>Analysis unavailable</strong>
              <span>{analysis.message}</span>
            </div>
          )}

          {analysis.status === "success" && (
            <AnalysisResult analysis={analysis.data} />
          )}
        </div>
      </section>
    </main>
  );
}

interface AnalysisResultProps {
  analysis: TicketAnalysis;
}

function AnalysisResult({ analysis }: AnalysisResultProps) {
  return (
    <article className="analysis-result">
      <div className="result-heading">
        <div>
          <span className="result-kicker">Analysis complete</span>
          <h2>Ticket assessment</h2>
        </div>
        <span
          className={`priority-badge priority-${analysis.priority.toLowerCase()}`}
        >
          {priorityLabels[analysis.priority]}
        </span>
      </div>

      <dl className="metadata-grid">
        <div>
          <dt>Category</dt>
          <dd>{analysis.category}</dd>
        </div>
        <div>
          <dt>Priority</dt>
          <dd>{analysis.priority}</dd>
        </div>
      </dl>

      <div className="result-copy">
        <section>
          <h3>Summary</h3>
          <p>{analysis.summary}</p>
        </section>
        <section>
          <h3>Suggested action</h3>
          <p>{analysis.suggestedAction}</p>
        </section>
      </div>
    </article>
  );
}

export default App;
