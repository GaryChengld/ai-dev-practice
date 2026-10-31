import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { streamTicketAgent } from "./ticketApi";
import type { AgentAnalysis, AgentStep } from "../types/AgentAnalysis";

const statusStep: AgentStep = { action: "CHECK_STATUS", result: "OPEN" };
const priorityStep: AgentStep = { action: "CHECK_PRIORITY", result: "HIGH" };
const completed: AgentAnalysis = {
  answer: "Ticket requires immediate attention",
  completed: true,
  status: "COMPLETED",
  steps: [statusStep, priorityStep],
};

function mockStream() {
  let controller!: ReadableStreamDefaultController<Uint8Array>;
  const cancel = vi.fn();
  const body = new ReadableStream<Uint8Array>({
    start(streamController) {
      controller = streamController;
    },
    cancel,
  });
  vi.mocked(fetch).mockResolvedValue(
    new Response(body, { headers: { "Content-Type": "text/event-stream" } })
  );
  return {
    body,
    controller,
    cancel,
    send(text: string) {
      controller.enqueue(new TextEncoder().encode(text));
    },
  };
}

describe("streamTicketAgent", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("reassembles fragmented events and delivers steps before the final result", async () => {
    const stream = mockStream();
    const onStep = vi.fn();
    const signal = new AbortController().signal;
    const result = streamTicketAgent("INC-1001", signal, onStep);

    stream.send('event:step\ndata:{"action":"CHECK_');
    stream.send('STATUS","result":"OPEN"}\n\n');
    await vi.waitFor(() => expect(onStep).toHaveBeenCalledExactlyOnceWith(statusStep));

    // Multiple frames can also arrive together, and complete need not wait for EOF.
    stream.send(
      `event:step\ndata:${JSON.stringify(priorityStep)}\n\n` +
      `event:complete\ndata:${JSON.stringify(completed)}\n\n`
    );

    await expect(result).resolves.toEqual(completed);
    expect(onStep.mock.calls).toEqual([[statusStep], [priorityStep]]);
    expect(fetch).toHaveBeenCalledExactlyOnceWith(
      expect.stringContaining("/api/agents/tickets/analyze/stream"),
      {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify({ ticketId: "INC-1001" }),
        signal,
      }
    );
    expect(stream.cancel).toHaveBeenCalledOnce();
    expect(stream.body.locked).toBe(false);
  });

  it("rejects when the stream closes after a step without a complete event", async () => {
    const stream = mockStream();
    const onStep = vi.fn();
    const result = streamTicketAgent("INC-1001", new AbortController().signal, onStep);
    stream.send(`event:step\ndata:${JSON.stringify(statusStep)}\n\n`);
    stream.controller.close();

    await expect(result).rejects.toThrow("Agent stream ended before a complete event.");
    expect(onStep).toHaveBeenCalledExactlyOnceWith(statusStep);
    expect(stream.body.locked).toBe(false);
  });

  it.each(["\n", "\r\n"])(
    "handles byte-by-byte UTF-8, multiline data, and %j line endings",
    async (newline) => {
      const stream = mockStream();
      const onStep = vi.fn();
      const step: AgentStep = { action: "CHECK_STATUS", result: "Ouvert café ☕" };
      const final: AgentAnalysis = { ...completed, answer: "Terminé ✓", steps: [step] };
      const result = streamTicketAgent("INC-1001", new AbortController().signal, onStep);
      const text = [
        ": keep-alive", "", "event:unknown", "data:ignored", "",
        "event: step", "data: {", 'data: "action": "CHECK_STATUS",',
        `data: "result": ${JSON.stringify(step.result)}}`, "",
        "event: complete", `data: ${JSON.stringify(final)}`, "", "",
      ].join(newline);

      // Splits every UTF-8 character and every CRLF pair across reads.
      for (const byte of new TextEncoder().encode(text)) {
        stream.controller.enqueue(Uint8Array.of(byte));
      }

      await expect(result).resolves.toEqual(final);
      expect(onStep).toHaveBeenCalledExactlyOnceWith(step);
    }
  );

  it("propagates cancellation while waiting for the next event", async () => {
    const stream = mockStream();
    const controller = new AbortController();
    const onStep = vi.fn();
    const result = streamTicketAgent("INC-1001", controller.signal, onStep);
    stream.send(`event:step\ndata:${JSON.stringify(statusStep)}\n\n`);
    await vi.waitFor(() => expect(onStep).toHaveBeenCalledOnce());

    controller.abort();

    await expect(result).rejects.toMatchObject({ name: "AbortError" });
    expect(stream.cancel).toHaveBeenCalledOnce();
    expect(onStep).toHaveBeenCalledTimes(1);
    expect(stream.body.locked).toBe(false);
  });

  it("rejects unsuccessful HTTP responses using the API error message", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ message: "Agent capacity is full" }), { status: 503 })
    );
    const onStep = vi.fn();

    await expect(
      streamTicketAgent("INC-1001", new AbortController().signal, onStep)
    ).rejects.toThrow("Agent capacity is full");
    expect(onStep).not.toHaveBeenCalled();
  });

  it("rejects a response without a readable body", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null));

    await expect(
      streamTicketAgent("INC-1001", new AbortController().signal, vi.fn())
    ).rejects.toThrow("The agent response has no readable stream.");
  });

  it("rejects malformed event JSON and releases the stream", async () => {
    const stream = mockStream();
    const onStep = vi.fn();
    const result = streamTicketAgent("INC-1001", new AbortController().signal, onStep);
    stream.send("event:step\ndata:{invalid}\n\n");

    await expect(result).rejects.toBeInstanceOf(SyntaxError);
    expect(onStep).not.toHaveBeenCalled();
    expect(stream.cancel).toHaveBeenCalledOnce();
    expect(stream.body.locked).toBe(false);
  });
});
