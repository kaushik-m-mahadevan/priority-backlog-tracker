import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TimeStageControl } from "./TimeTracking";
import type { TimeLogEntryView } from "./types";

function entry(hours: number): TimeLogEntryView {
  return { entryId: "e", stage: "CRAFTING", hours, date: "2026-01-01T00:00:00Z", loggedByCreatorId: "c1", note: null };
}

describe("TimeStageControl — logged/estimated summary text", () => {
  it("shows only the logged total when there's no estimate yet", () => {
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={0} entries={[entry(2)]} onLog={vi.fn()} />);
    expect(screen.getByText("2.00h logged")).toBeInTheDocument();
    expect(screen.queryByText(/estimated/)).not.toBeInTheDocument();
  });

  it("shows the estimate and how much remains when under it", () => {
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={5} entries={[entry(2)]} onLog={vi.fn()} />);
    expect(screen.getByText(/2\.00h logged/)).toBeInTheDocument();
    expect(screen.getByText(/5\.00h estimated/)).toBeInTheDocument();
    expect(screen.getByText(/3\.00h remaining/)).toBeInTheDocument();
  });

  it("shows an over-estimate note once logged hours exceed the estimate", () => {
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={5} entries={[entry(3), entry(4)]} onLog={vi.fn()} />);
    expect(screen.getByText(/7\.00h logged/)).toBeInTheDocument();
    expect(screen.getByText(/2\.00h over estimate/)).toBeInTheDocument();
  });

  it("shows neither over-estimate nor remaining when logged exactly matches the estimate", () => {
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={4} entries={[entry(4)]} onLog={vi.fn()} />);
    expect(screen.queryByText(/over estimate/)).not.toBeInTheDocument();
    expect(screen.queryByText(/remaining/)).not.toBeInTheDocument();
  });

  it("sums every entry's hours toward the logged total, not just the first", () => {
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={0} entries={[entry(1), entry(2), entry(0.5)]} onLog={vi.fn()} />);
    expect(screen.getByText("3.50h logged")).toBeInTheDocument();
  });
});

describe("TimeStageControl — the logging popover", () => {
  it("opens the slider on click, starting at 0h", async () => {
    const user = userEvent.setup();
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={0} entries={[]} onLog={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Log Crochet time" }));
    expect(screen.getByRole("slider", { name: "Crochet hours" })).toBeInTheDocument();
    expect(screen.getByText("0.00h")).toBeInTheDocument();
  });

  it("ArrowUp/ArrowDown on the slider step the value by 0.25h, clamped at 0 and 12", async () => {
    const user = userEvent.setup();
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={0} entries={[]} onLog={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Log Crochet time" }));
    const slider = screen.getByRole("slider", { name: "Crochet hours" });
    slider.focus();

    await user.keyboard("{ArrowDown}");
    expect(screen.getByText("0.00h")).toBeInTheDocument(); // already at the floor

    await user.keyboard("{ArrowUp}{ArrowUp}{ArrowUp}");
    expect(screen.getByText("0.75h")).toBeInTheDocument();
  });

  it("confirming with a positive value calls onLog and closes the popover", async () => {
    const user = userEvent.setup();
    const onLog = vi.fn().mockResolvedValue(undefined);
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={0} entries={[]} onLog={onLog} />);
    await user.click(screen.getByRole("button", { name: "Log Crochet time" }));
    screen.getByRole("slider", { name: "Crochet hours" }).focus();
    await user.keyboard("{ArrowUp}{ArrowUp}"); // 0.5h

    await user.click(screen.getByRole("button", { name: "Confirm Crochet time" }));

    expect(onLog).toHaveBeenCalledWith(0.5);
    expect(screen.queryByRole("slider", { name: "Crochet hours" })).not.toBeInTheDocument();
  });

  it("confirming at 0h closes without ever calling onLog", async () => {
    const user = userEvent.setup();
    const onLog = vi.fn();
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={0} entries={[]} onLog={onLog} />);
    await user.click(screen.getByRole("button", { name: "Log Crochet time" }));

    await user.click(screen.getByRole("button", { name: "Confirm Crochet time" }));

    expect(onLog).not.toHaveBeenCalled();
    expect(screen.queryByRole("slider", { name: "Crochet hours" })).not.toBeInTheDocument();
  });

  it("cancel resets the value and closes without calling onLog", async () => {
    const user = userEvent.setup();
    const onLog = vi.fn();
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={0} entries={[]} onLog={onLog} />);
    await user.click(screen.getByRole("button", { name: "Log Crochet time" }));
    screen.getByRole("slider", { name: "Crochet hours" }).focus();
    await user.keyboard("{ArrowUp}");

    await user.click(screen.getByRole("button", { name: "Cancel Crochet time" }));

    expect(onLog).not.toHaveBeenCalled();
    expect(screen.queryByRole("slider", { name: "Crochet hours" })).not.toBeInTheDocument();
  });

  it("shows an error message and keeps the popover open when onLog rejects", async () => {
    const user = userEvent.setup();
    const onLog = vi.fn().mockRejectedValue(new Error("Server refused it"));
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={0} entries={[]} onLog={onLog} />);
    await user.click(screen.getByRole("button", { name: "Log Crochet time" }));
    screen.getByRole("slider", { name: "Crochet hours" }).focus();
    await user.keyboard("{ArrowUp}");

    await user.click(screen.getByRole("button", { name: "Confirm Crochet time" }));

    expect(await screen.findByText("Server refused it")).toBeInTheDocument();
    expect(screen.getByRole("slider", { name: "Crochet hours" })).toBeInTheDocument();
  });

  it("clicking the trigger again while open closes the popover instead of reopening it", async () => {
    const user = userEvent.setup();
    render(<TimeStageControl icon="🧶" label="Crochet" estimatedHours={0} entries={[]} onLog={vi.fn()} />);
    const trigger = screen.getByRole("button", { name: "Log Crochet time" });
    await user.click(trigger);
    expect(screen.getByRole("slider", { name: "Crochet hours" })).toBeInTheDocument();

    await user.click(trigger);
    expect(screen.queryByRole("slider", { name: "Crochet hours" })).not.toBeInTheDocument();
  });
});
