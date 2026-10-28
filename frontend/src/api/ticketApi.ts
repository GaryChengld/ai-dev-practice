import type { TicketAnalysis } from "../types/TicketAnalysis";
import type { AgentAnalysis } from "../types/AgentAnalysis";

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

async function readError(response: Response): Promise<string | undefined> {
  try {
    const error = (await response.json()) as ApiError;
    return error.message;
  } catch {
    return undefined;
  }
}
