import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  approveEscalation,
  createEscalationProposal,
  EscalationApiError,
  rejectEscalation,
} from "./escalationApi";

const proposal = {
  id: "7a362466-78dd-4ef4-aeac-5d07876b8b9c",
  ticketId: "INC-1001",
  reason: "Completed analysis found HIGH priority.",
};
const fetchMock = vi.fn<typeof fetch>();

describe("escalation API", () => {
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("creates a proposal using only the ticket ID", async () => {
    fetchMock.mockResolvedValue(Response.json(proposal, { status: 201 }));
    await expect(createEscalationProposal("INC-1001")).resolves.toEqual(proposal);
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(
      expect.stringMatching(/\/api\/agents\/tickets\/escalation-proposals$/),
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ticketId: "INC-1001" }),
      }
    );
  });

  it("returns null for a 204 without trying to parse JSON", async () => {
    const response = new Response(null, { status: 204 });
    const json = vi.spyOn(response, "json");
    fetchMock.mockResolvedValue(response);
    await expect(createEscalationProposal("INC-1001")).resolves.toBeNull();
    expect(json).not.toHaveBeenCalled();
  });

  it.each([
    ["approve", approveEscalation],
    ["reject", rejectEscalation],
  ] as const)("submits %s with only the proposal UUID in the URL", async (decision, submit) => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(submit(proposal.id)).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(
      expect.stringContaining(`/escalation-proposals/${proposal.id}/${decision}`),
      { method: "POST" }
    );
  });

  it.each([
    [401, "Please sign in"],
    [403, "do not have permission"],
    [409, "no longer pending"],
    [500, "status 500"],
  ])("classifies HTTP %s explicitly without retrying", async (status, message) => {
    fetchMock.mockResolvedValue(new Response(null, { status }));
    await expect(approveEscalation(proposal.id)).rejects.toMatchObject({
      name: "EscalationApiError", status, message: expect.stringContaining(message),
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("rejects proposal creation failures", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 503 }));
    await expect(createEscalationProposal("INC-1001")).rejects.toBeInstanceOf(EscalationApiError);
  });

  it("rejects a rejection request that does not return 204", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    await expect(rejectEscalation(proposal.id)).rejects.toMatchObject({ status: 200 });
  });

  it("does not treat HTTP 200 as an approved escalation", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));
    await expect(approveEscalation(proposal.id)).rejects.toMatchObject({ status: 200 });
  });

  it("requires 201 for a returned proposal", async () => {
    fetchMock.mockResolvedValue(Response.json(proposal, { status: 200 }));
    await expect(createEscalationProposal("INC-1001")).rejects.toMatchObject({ status: 200 });
  });

  it("rejects an invalid proposal payload", async () => {
    fetchMock.mockResolvedValue(Response.json({ ...proposal, id: null }, { status: 201 }));
    await expect(createEscalationProposal("INC-1001")).rejects.toThrow("Invalid escalation proposal response.");
  });

  it("propagates network failures", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));
    await expect(createEscalationProposal("INC-1001")).rejects.toThrow("Failed to fetch");
  });
});
