import type { EscalationProposal } from "../types/EscalationProposal";

const PROPOSALS_URL = `${import.meta.env.VITE_API_BASE_URL ?? ""}/api/agents/tickets/escalation-proposals`;

export class EscalationApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "EscalationApiError";
  }
}

async function requireStatus(response: Response, expected: number): Promise<void> {
  if (response.status === expected) return;

  const messages: Record<number, string> = {
    401: "Please sign in before reviewing escalations.",
    403: "You do not have permission to manage ticket escalations.",
    409: "This proposal is no longer pending. Request a new review.",
  };
  throw new EscalationApiError(
    response.status,
    messages[response.status] ??
      `Escalation request failed with status ${response.status}. Please try again later.`
  );
}

export async function createEscalationProposal(
  ticketId: string
): Promise<EscalationProposal | null> {
  const response = await fetch(PROPOSALS_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ticketId }),
  });
  if (response.status === 204) return null;
  await requireStatus(response, 201);

  const value: unknown = await response.json();
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error("Invalid escalation proposal response.");
  }
  const proposal = value as Record<string, unknown>;
  if (
    typeof proposal.id !== "string" || !proposal.id.trim() ||
    typeof proposal.ticketId !== "string" || !proposal.ticketId.trim() ||
    typeof proposal.reason !== "string"
  ) {
    throw new Error("Invalid escalation proposal response.");
  }
  return { id: proposal.id, ticketId: proposal.ticketId, reason: proposal.reason };
}

export async function approveEscalation(id: string): Promise<void> {
  await submitDecision(id, "approve");
}

export async function rejectEscalation(id: string): Promise<void> {
  await submitDecision(id, "reject");
}

async function submitDecision(id: string, decision: "approve" | "reject"): Promise<void> {
  const response = await fetch(`${PROPOSALS_URL}/${encodeURIComponent(id)}/${decision}`, {
    method: "POST",
  });
  await requireStatus(response, 204);
}
