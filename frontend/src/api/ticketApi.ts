import type { TicketAnalysis } from "../types/TicketAnalysis";
import type { AgentAnalysis, AgentStep } from "../types/AgentAnalysis";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

interface ApiError {
  message?: string;
}

export async function analyzeTicket(message: string): Promise<TicketAnalysis> {
  const response = await fetch(`${API_BASE_URL}/api/ai/analyze-ticket`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ message }),
  });

  if (!response.ok) {
    const error = await readError(response);
    throw new Error(error ?? `Request failed with status ${response.status}.`);
  }

  return (await response.json()) as TicketAnalysis;
}

export async function analyzeTicketWithAgent(
  ticketId: string,
  signal?: AbortSignal
): Promise<AgentAnalysis> {
  const response = await fetch(`${API_BASE_URL}/api/agents/tickets/analyze`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ ticketId }),
    signal,
  });

  if (!response.ok) {
    const error = await readError(response);
    throw new Error(error ?? `Request failed with status ${response.status}.`);
  }

  return (await response.json()) as AgentAnalysis;
}

export async function streamTicketAgent(
  ticketId: string,
  signal: AbortSignal,
  onStep: (step: AgentStep) => void
): Promise<AgentAnalysis> {
  signal.throwIfAborted();
  const response = await fetch(`${API_BASE_URL}/api/agents/tickets/analyze/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    },
    body: JSON.stringify({ ticketId }),
    signal,
  });

  if (!response.ok) {
    const error = await readError(response);
    throw new Error(error ?? `Request failed with status ${response.status}.`);
  }
  if (!response.body) {
    throw new Error("The agent response has no readable stream.");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  const cancelReader = () => {
    void reader.cancel(signal.reason).catch(() => {});
  };
  signal.addEventListener("abort", cancelReader, { once: true });

  try {
    while (true) {
      signal.throwIfAborted();
      const { value, done } = await reader.read();
      signal.throwIfAborted();
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true });

      // Keep incomplete frames, including a trailing CR, until the next read.
      let boundary: RegExpExecArray | null;
      while ((boundary = /\r?\n\r?\n/.exec(buffer)) !== null) {
        const frame = buffer.slice(0, boundary.index);
        buffer = buffer.slice(boundary.index + boundary[0].length);
        const event = parseAgentEvent(frame);
        signal.throwIfAborted();
        if (event?.name === "step") {
          onStep(event.data);
        } else if (event?.name === "complete") {
          return event.data;
        }
      }

      if (done) {
        throw new Error("Agent stream ended before a complete event.");
      }
    }
  } finally {
    signal.removeEventListener("abort", cancelReader);
    try {
      await reader.cancel();
    } catch {
      // Cleanup must not replace the result, stream error, or abort reason.
    } finally {
      reader.releaseLock();
    }
  }
}

type AgentEvent =
  | { name: "step"; data: AgentStep }
  | { name: "complete"; data: AgentAnalysis };

function isAgentStep(value: unknown): value is AgentStep {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return false;
  }

  const step = value as Record<string, unknown>;
  return (
    (step.action === "CHECK_STATUS" || step.action === "CHECK_PRIORITY") &&
    typeof step.result === "string"
  );
}

function isAgentAnalysis(value: unknown): value is AgentAnalysis {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return false;
  }

  const analysis = value as Record<string, unknown>;
  return (
    typeof analysis.answer === "string" &&
    typeof analysis.completed === "boolean" &&
    (analysis.status === "COMPLETED" ||
      analysis.status === "STEP_LIMIT_REACHED" ||
      analysis.status === "PLANNER_ERROR" ||
      analysis.status === "TOOL_ERROR") &&
    analysis.completed === (analysis.status === "COMPLETED") &&
    Array.isArray(analysis.steps) &&
    analysis.steps.every(isAgentStep)
  );
}

function parseAgentEvent(frame: string): AgentEvent | undefined {
  let name = "";
  const data: string[] = [];
  for (const line of frame.split(/\r?\n/)) {
    const colon = line.indexOf(":");
    if (colon < 0) continue;
    const field = line.slice(0, colon);
    const value = line.slice(colon + 1).replace(/^ /, "");
    if (field === "event") name = value;
    if (field === "data") data.push(value);
  }

  // Ignore comments, keep-alives, and events outside the agent contract.
  if (name === "step") {
    const payload: unknown = data.length === 0 ? undefined : JSON.parse(data.join("\n"));
    if (!isAgentStep(payload)) {
      throw new Error("Invalid agent step event.");
    }
    return { name, data: payload };
  }
  if (name === "complete") {
    const payload: unknown = data.length === 0 ? undefined : JSON.parse(data.join("\n"));
    if (!isAgentAnalysis(payload)) {
      throw new Error("Invalid agent complete event.");
    }
    return { name, data: payload };
  }
}

async function readError(response: Response): Promise<string | undefined> {
  try {
    const error = (await response.json()) as ApiError;
    return error.message;
  } catch {
    return undefined;
  }
}
