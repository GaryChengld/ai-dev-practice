import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import TicketAgentPanel from "./TicketAgentPanel";
import { analyzeTicketWithAgent } from "../api/ticketApi";

vi.mock("../api/ticketApi", () => ({
  analyzeTicketWithAgent: vi.fn(),
}));

describe("TicketAgentPanel", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("displays a completed agent analysis", async () => {
    vi.mocked(analyzeTicketWithAgent).mockResolvedValue({
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

    expect(analyzeTicketWithAgent).toHaveBeenCalledExactlyOnceWith("INC-1001");
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
    vi.mocked(analyzeTicketWithAgent).mockResolvedValue({
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

    expect(analyzeTicketWithAgent).toHaveBeenCalledExactlyOnceWith("INC-1001");
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
    vi.mocked(analyzeTicketWithAgent).mockRejectedValue(
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
    expect(analyzeTicketWithAgent).toHaveBeenCalledExactlyOnceWith("INC-1001");
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
    expect(analyzeTicketWithAgent).not.toHaveBeenCalled();
  });
});
