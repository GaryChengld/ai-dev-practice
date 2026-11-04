import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import TicketEscalationPanel from "./TicketEscalationPanel";
import {
  approveEscalation,
  createEscalationProposal,
  EscalationApiError,
  rejectEscalation,
} from "../api/escalationApi";
import type { EscalationProposal } from "../types/EscalationProposal";

vi.mock("../api/escalationApi", async (importOriginal) => ({
  ...await importOriginal<typeof import("../api/escalationApi")>(),
  createEscalationProposal: vi.fn(),
  approveEscalation: vi.fn(),
  rejectEscalation: vi.fn(),
}));

const proposal: EscalationProposal = {
  id: "7a362466-78dd-4ef4-aeac-5d07876b8b9c",
  ticketId: "INC-1001",
  reason: "Completed analysis found HIGH priority.",
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

async function requestReview(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Request escalation review" }));
}

describe("TicketEscalationPanel", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(createEscalationProposal).mockResolvedValue(proposal);
    vi.mocked(approveEscalation).mockResolvedValue(undefined);
    vi.mocked(rejectEscalation).mockResolvedValue(undefined);
  });

  it("displays a proposal for explicit human review without automatically approving", async () => {
    const user = userEvent.setup();
    render(<TicketEscalationPanel ticketId="INC-1001" />);
    expect(createEscalationProposal).not.toHaveBeenCalled();
    await requestReview(user);
    expect(createEscalationProposal).toHaveBeenCalledExactlyOnceWith("INC-1001");
    expect(await screen.findByText("Ticket ID: INC-1001")).toBeInTheDocument();
    expect(screen.getByText(proposal.reason)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Proposed action: Escalate ticket" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Reject" })).toBeEnabled();
    expect(approveEscalation).not.toHaveBeenCalled();
    expect(rejectEscalation).not.toHaveBeenCalled();
  });

  it("approves exactly once using only the server proposal UUID", async () => {
    const user = userEvent.setup();
    render(<TicketEscalationPanel ticketId="INC-1001" />);
    await requestReview(user);
    await user.click(screen.getByRole("button", { name: "Approve" }));
    expect(approveEscalation).toHaveBeenCalledExactlyOnceWith(proposal.id);
    expect(rejectEscalation).not.toHaveBeenCalled();
    expect(await screen.findByText("Escalation approved.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  it("rejects without calling the approval API", async () => {
    const user = userEvent.setup();
    render(<TicketEscalationPanel ticketId="INC-1001" />);
    await requestReview(user);
    await user.click(screen.getByRole("button", { name: "Reject" }));
    expect(rejectEscalation).toHaveBeenCalledExactlyOnceWith(proposal.id);
    expect(approveEscalation).not.toHaveBeenCalled();
    expect(await screen.findByText("Escalation rejected.")).toBeInTheDocument();
  });

  it.each(["Approve", "Reject"])("disables both decisions while %s is pending and prevents duplicate submission", async (decision) => {
    const pending = deferred<void>();
    const submit = decision === "Approve" ? approveEscalation : rejectEscalation;
    vi.mocked(submit).mockReturnValue(pending.promise);
    const user = userEvent.setup();
    render(<TicketEscalationPanel ticketId="INC-1001" />);
    await requestReview(user);
    await user.dblClick(screen.getByRole("button", { name: decision }));
    expect(screen.getByRole("button", { name: "Approve" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Reject" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Request escalation review" })).toBeDisabled();
    expect(screen.getByText("Submitting your decision…")).toBeInTheDocument();
    expect(screen.queryByText(/Escalation (approved|rejected)\./)).not.toBeInTheDocument();
    expect(submit).toHaveBeenCalledExactlyOnceWith(proposal.id);
    await user.click(screen.getByRole("button", { name: decision === "Approve" ? "Reject" : "Approve" }));
    expect(approveEscalation).toHaveBeenCalledTimes(decision === "Approve" ? 1 : 0);
    expect(rejectEscalation).toHaveBeenCalledTimes(decision === "Reject" ? 1 : 0);
    await act(async () => pending.resolve(undefined));
    expect(screen.getByText(`Escalation ${decision === "Approve" ? "approved" : "rejected"}.`)).toBeInTheDocument();
  });

  it("shows a stale-proposal error for HTTP 409 without retrying", async () => {
    vi.mocked(approveEscalation).mockRejectedValue(new EscalationApiError(
      409, "This proposal is no longer pending. Request a new review."
    ));
    const user = userEvent.setup();
    render(<TicketEscalationPanel ticketId="INC-1001" />);
    await requestReview(user);
    await user.click(screen.getByRole("button", { name: "Approve" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("no longer pending");
    expect(approveEscalation).toHaveBeenCalledExactlyOnceWith(proposal.id);
    expect(createEscalationProposal).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.queryByText("Escalation approved.")).not.toBeInTheDocument();
  });

  it("discards the old proposal when the ticket changes", async () => {
    const user = userEvent.setup();
    const { rerender } = render(<TicketEscalationPanel ticketId="INC-1001" />);
    await requestReview(user);
    expect(await screen.findByRole("button", { name: "Approve" })).toBeInTheDocument();
    rerender(<TicketEscalationPanel ticketId="INC-1002" />);
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reject" })).not.toBeInTheDocument();
    expect(screen.queryByText(proposal.reason)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Request escalation review" })).toBeEnabled();
    expect(approveEscalation).not.toHaveBeenCalled();
  });

  it("ignores an old proposal arriving after a ticket change", async () => {
    const old = deferred<EscalationProposal | null>();
    vi.mocked(createEscalationProposal)
      .mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce({ ...proposal, id: "new-proposal-id", ticketId: "INC-1002", reason: "New ticket review" });
    const user = userEvent.setup();
    const { rerender } = render(<TicketEscalationPanel ticketId="INC-1001" />);
    await requestReview(user);
    rerender(<TicketEscalationPanel ticketId="INC-1002" />);
    await requestReview(user);
    expect(await screen.findByText("New ticket review")).toBeInTheDocument();
    await act(async () => old.resolve(proposal));
    expect(screen.queryByText(proposal.reason)).not.toBeInTheDocument();
    expect(screen.getByText("Ticket ID: INC-1002")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Approve" }));
    expect(approveEscalation).toHaveBeenCalledExactlyOnceWith("new-proposal-id");
  });

  it("ignores an old decision settling after a ticket change", async () => {
    const pending = deferred<void>();
    vi.mocked(approveEscalation).mockReturnValueOnce(pending.promise);
    const user = userEvent.setup();
    const { rerender } = render(<TicketEscalationPanel ticketId="INC-1001" />);
    await requestReview(user);
    await user.click(screen.getByRole("button", { name: "Approve" }));
    rerender(<TicketEscalationPanel ticketId="INC-1002" />);
    await act(async () => pending.resolve(undefined));
    expect(screen.queryByText("Escalation approved.")).not.toBeInTheDocument();
    expect(screen.getByText("No escalation has been proposed.")).toBeInTheDocument();
  });

  it("handles an ineligible ticket without decision controls", async () => {
    vi.mocked(createEscalationProposal).mockResolvedValue(null);
    const user = userEvent.setup();
    render(<TicketEscalationPanel ticketId="INC-1001" />);
    await requestReview(user);
    expect(await screen.findByText("No escalation proposal is available for this ticket.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  it("shows proposal request failures and allows a new manual request", async () => {
    vi.mocked(createEscalationProposal).mockRejectedValue(new Error("Service unavailable"));
    const user = userEvent.setup();
    render(<TicketEscalationPanel ticketId="INC-1001" />);
    await requestReview(user);
    expect(await screen.findByRole("alert")).toHaveTextContent("Service unavailable");
    expect(screen.getByRole("button", { name: "Request escalation review" })).toBeEnabled();
    expect(approveEscalation).not.toHaveBeenCalled();
  });

  it("does not request review for a blank ticket", async () => {
    const user = userEvent.setup();
    render(<TicketEscalationPanel ticketId="   " />);
    await requestReview(user);
    expect(await screen.findByRole("alert")).toHaveTextContent("Please select a ticket ID.");
    expect(createEscalationProposal).not.toHaveBeenCalled();
  });
});
