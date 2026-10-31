import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import TicketAgentPanel from "./TicketAgentPanel";
import { streamTicketAgent } from "../api/ticketApi";
import type { AgentAnalysis } from "../types/AgentAnalysis";

vi.mock("../api/ticketApi", () => ({
  streamTicketAgent: vi.fn(),
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function completedAnalysis(answer: string): AgentAnalysis {
  return { answer, completed: true, status: "COMPLETED", steps: [] };
}

describe("TicketAgentPanel", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("displays a completed agent analysis", async () => {
    vi.mocked(streamTicketAgent).mockResolvedValue({
      answer: "Ticket requires immediate attention",
      completed: true,
      status: "COMPLETED",
      steps: [
        { action: "CHECK_STATUS", result: "OPEN" },
        { action: "CHECK_PRIORITY", result: "HIGH" },
      ],
    });

    const user = userEvent.setup();
    render(<TicketAgentPanel />);

    await user.type(
      screen.getByRole("textbox", { name: /ticket id/i }),
      "INC-1001"
    );
    await user.click(screen.getByRole("button", { name: /run ai agent/i }));

    expect(streamTicketAgent).toHaveBeenCalledExactlyOnceWith(
      "INC-1001", expect.any(AbortSignal), expect.any(Function)
    );
    expect(
      await screen.findByText("Ticket requires immediate attention")
    ).toBeInTheDocument();
    expect(screen.getByText("Status: COMPLETED")).toBeInTheDocument();
    expect(screen.getByText("2 successful tool executions")).toBeInTheDocument();

    const steps = screen.getAllByRole("listitem");
    expect(steps).toHaveLength(2);
    expect(within(steps[0]).getByText("CHECK_STATUS")).toBeInTheDocument();
    expect(within(steps[0]).getByText("OPEN")).toBeInTheDocument();
    expect(within(steps[1]).getByText("CHECK_PRIORITY")).toBeInTheDocument();
    expect(within(steps[1]).getByText("HIGH")).toBeInTheDocument();
  });

  it("displays tool failure with completed execution history", async () => {
    vi.mocked(streamTicketAgent).mockResolvedValue({
      answer: "Unable to retrieve ticket priority",
      completed: false,
      status: "TOOL_ERROR",
      steps: [{ action: "CHECK_STATUS", result: "OPEN" }],
    });

    const user = userEvent.setup();
    render(<TicketAgentPanel />);

    await user.type(
      screen.getByRole("textbox", { name: /ticket id/i }),
      "INC-1001"
    );
    await user.click(screen.getByRole("button", { name: /run ai agent/i }));

    expect(streamTicketAgent).toHaveBeenCalledExactlyOnceWith(
      "INC-1001", expect.any(AbortSignal), expect.any(Function)
    );
    expect(
      await screen.findByText("Unable to retrieve ticket priority")
    ).toBeInTheDocument();
    expect(screen.getByText("Status: TOOL_ERROR")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Tool execution failed" })
    ).toBeInTheDocument();
    expect(screen.getByText("1 successful tool execution")).toBeInTheDocument();

    const steps = screen.getAllByRole("listitem");
    expect(steps).toHaveLength(1);
    expect(within(steps[0]).getByText("CHECK_STATUS")).toBeInTheDocument();
    expect(within(steps[0]).getByText("OPEN")).toBeInTheDocument();
    expect(screen.queryByText("Unable to run the agent")).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("displays an error when the API request fails", async () => {
    vi.mocked(streamTicketAgent).mockRejectedValue(
      new Error("Service unavailable")
    );

    const user = userEvent.setup();
    render(<TicketAgentPanel />);

    await user.type(
      screen.getByRole("textbox", { name: /ticket id/i }),
      "INC-1001"
    );
    await user.click(screen.getByRole("button", { name: /run ai agent/i }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Unable to run the agent");
    expect(alert).toHaveTextContent("Service unavailable");
    expect(streamTicketAgent).toHaveBeenCalledExactlyOnceWith(
      "INC-1001", expect.any(AbortSignal), expect.any(Function)
    );
    expect(
      screen.queryByRole("heading", { name: "Execution history" })
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("list")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /run ai agent/i })).toBeEnabled();
  });

  it("does not call the API for a blank ticket ID", async () => {
    const user = userEvent.setup();
    render(<TicketAgentPanel />);

    await user.type(screen.getByRole("textbox", { name: /ticket id/i }), "   ");
    await user.click(screen.getByRole("button", { name: /run ai agent/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Please enter a ticket ID."
    );
    expect(streamTicketAgent).not.toHaveBeenCalled();
  });

  it("displays each completed tool while the stream is pending", async () => {
    const pending = deferred<AgentAnalysis>();
    vi.mocked(streamTicketAgent).mockReturnValueOnce(pending.promise);
    const user = userEvent.setup();
    render(<TicketAgentPanel />);
    await user.type(screen.getByRole("textbox", { name: /ticket id/i }), "INC-1001");
    await user.click(screen.getByRole("button", { name: /run ai agent/i }));
    const onStep = vi.mocked(streamTicketAgent).mock.calls[0][2];

    act(() => onStep({ action: "CHECK_STATUS", result: "OPEN" }));
    expect(screen.getByText("OPEN")).toBeInTheDocument();
    expect(screen.getByText("1 successful tool execution")).toBeInTheDocument();
    expect(screen.getByText("Analyzing the ticket. Please wait.")).toBeInTheDocument();
    expect(screen.queryByText("Status: COMPLETED")).not.toBeInTheDocument();

    act(() => onStep({ action: "CHECK_PRIORITY", result: "HIGH" }));
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(screen.getByText("HIGH")).toBeInTheDocument();

    await act(async () => {
      pending.resolve({
        ...completedAnalysis("Analysis finished"),
        steps: [
          { action: "CHECK_STATUS", result: "OPEN" },
          { action: "CHECK_PRIORITY", result: "HIGH" },
        ],
      });
    });
    expect(screen.getByText("Analysis finished")).toBeInTheDocument();
    expect(screen.getByText("Status: COMPLETED")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(screen.queryByText("Analyzing the ticket. Please wait.")).not.toBeInTheDocument();
  });

  it("ignores obsolete stream progress while showing only the newest ticket", async () => {
    const first = deferred<AgentAnalysis>();
    const second = deferred<AgentAnalysis>();
    vi.mocked(streamTicketAgent)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const user = userEvent.setup();
    render(<TicketAgentPanel />);
    const input = screen.getByRole("textbox", { name: /ticket id/i });
    const submit = screen.getByRole("button", { name: /run ai agent/i });
    await user.type(input, "INC-1001");
    await user.click(submit);
    const [, firstSignal, oldStep] = vi.mocked(streamTicketAgent).mock.calls[0];
    act(() => oldStep({ action: "CHECK_STATUS", result: "Old progress" }));
    expect(screen.getByText("Old progress")).toBeInTheDocument();

    await user.clear(input);
    await user.type(input, "INC-1002");
    await user.click(submit);
    const newStep = vi.mocked(streamTicketAgent).mock.calls[1][2];
    expect(firstSignal.aborted).toBe(true);
    expect(screen.queryByText("Old progress")).not.toBeInTheDocument();
    act(() => {
      oldStep({ action: "CHECK_PRIORITY", result: "Late obsolete progress" });
      newStep({ action: "CHECK_STATUS", result: "New progress" });
    });
    await act(async () => {
      first.resolve(completedAnalysis("Obsolete completion"));
    });
    expect(screen.queryByText("Late obsolete progress")).not.toBeInTheDocument();
    expect(screen.queryByText("Obsolete completion")).not.toBeInTheDocument();
    expect(screen.getByText("New progress")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(1);

    await act(async () => {
      second.resolve({
        ...completedAnalysis("Newest analysis finished"),
        steps: [{ action: "CHECK_STATUS", result: "New progress" }],
      });
    });
    act(() => oldStep({ action: "CHECK_PRIORITY", result: "Even later progress" }));
    expect(screen.getByText("Newest analysis finished")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(1);
    expect(screen.queryByText("Even later progress")).not.toBeInTheDocument();
  });

  it("keeps the newest result when an older request finishes last", async () => {
    const first = deferred<AgentAnalysis>();
    const second = deferred<AgentAnalysis>();
    vi.mocked(streamTicketAgent)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);

    const user = userEvent.setup();
    render(<TicketAgentPanel />);
    const input = screen.getByRole("textbox", { name: /ticket id/i });
    const submit = screen.getByRole("button", { name: /run ai agent/i });

    await user.type(input, "INC-1001");
    await user.click(submit);
    const firstSignal = vi.mocked(streamTicketAgent).mock.calls[0][1];
    expect(firstSignal.aborted).toBe(false);
    expect(input).toBeEnabled();
    expect(submit).toBeEnabled();

    await user.clear(input);
    await user.type(input, "INC-1002");
    await user.click(submit);

    expect(streamTicketAgent).toHaveBeenCalledTimes(2);
    expect(streamTicketAgent).toHaveBeenNthCalledWith(
      2, "INC-1002", expect.any(AbortSignal), expect.any(Function)
    );
    expect(firstSignal.aborted).toBe(true);
    expect(vi.mocked(streamTicketAgent).mock.calls[1][1].aborted).toBe(false);

    await act(async () => {
      second.resolve(completedAnalysis("INC-1002 is the latest result"));
    });
    expect(screen.getByText("INC-1002 is the latest result")).toBeInTheDocument();

    await act(async () => {
      first.resolve(completedAnalysis("INC-1001 is the obsolete result"));
    });
    expect(screen.getByText("INC-1002 is the latest result")).toBeInTheDocument();
    expect(screen.queryByText("INC-1001 is the obsolete result")).not.toBeInTheDocument();
  });

  it("ignores an obsolete request error while the newest request is pending", async () => {
    const first = deferred<AgentAnalysis>();
    const second = deferred<AgentAnalysis>();
    vi.mocked(streamTicketAgent)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);

    const user = userEvent.setup();
    render(<TicketAgentPanel />);
    const input = screen.getByRole("textbox", { name: /ticket id/i });
    const submit = screen.getByRole("button", { name: /run ai agent/i });
    await user.type(input, "INC-1001");
    await user.click(submit);
    await user.clear(input);
    await user.type(input, "INC-1002");
    await user.click(submit);

    await act(async () => {
      first.reject(new Error("Obsolete request failed"));
    });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("Analyzing the ticket. Please wait.")).toBeInTheDocument();

    await act(async () => {
      second.resolve(completedAnalysis("Latest analysis completed"));
    });
    expect(screen.getByText("Latest analysis completed")).toBeInTheDocument();
  });

  it("cancels a pending request before displaying a blank ticket validation error", async () => {
    const pending = deferred<AgentAnalysis>();
    vi.mocked(streamTicketAgent).mockReturnValueOnce(pending.promise);

    const user = userEvent.setup();
    render(<TicketAgentPanel />);
    const input = screen.getByRole("textbox", { name: /ticket id/i });
    const submit = screen.getByRole("button", { name: /run ai agent/i });
    await user.type(input, "INC-1001");
    await user.click(submit);
    const signal = vi.mocked(streamTicketAgent).mock.calls[0][1];
    await user.clear(input);
    await user.type(input, "   ");
    await user.click(submit);

    expect(signal.aborted).toBe(true);
    expect(streamTicketAgent).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("alert")).toHaveTextContent("Please enter a ticket ID.");

    await act(async () => {
      pending.resolve(completedAnalysis("Obsolete analysis"));
    });
    expect(screen.getByRole("alert")).toHaveTextContent("Please enter a ticket ID.");
    expect(screen.queryByText("Obsolete analysis")).not.toBeInTheDocument();
  });

  it("aborts the active request when unmounted", async () => {
    const pending = deferred<AgentAnalysis>();
    vi.mocked(streamTicketAgent).mockReturnValueOnce(pending.promise);

    const user = userEvent.setup();
    const { unmount } = render(<TicketAgentPanel />);
    await user.type(screen.getByRole("textbox", { name: /ticket id/i }), "INC-1001");
    await user.click(screen.getByRole("button", { name: /run ai agent/i }));
    const signal = vi.mocked(streamTicketAgent).mock.calls[0][1];
    expect(signal.aborted).toBe(false);

    unmount();
    expect(signal.aborted).toBe(true);
    await act(async () => {
      pending.reject(new DOMException("Request aborted", "AbortError"));
    });
  });
});
