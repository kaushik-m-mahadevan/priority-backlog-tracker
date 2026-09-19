import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { OrderDueDate } from "./OrderDueDate";

describe("OrderDueDate", () => {
  beforeEach(() => {
    vi.setSystemTime(new Date("2026-06-15T12:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows the plain formatted date with no overdue marker when due in the future", () => {
    const { container } = render(<OrderDueDate iso="2026-06-20T00:00:00Z" status="IN_PROGRESS" />);
    expect(container.querySelector(".bang")).not.toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });

  it("flags a past due date as overdue with the '!' marker, for a live order", () => {
    const { container } = render(<OrderDueDate iso="2026-06-10T00:00:00Z" status="IN_PROGRESS" />);
    expect(container.querySelector(".bang")).toBeInTheDocument();
    expect(container.querySelector(".bang")).toHaveAttribute("title", "Overdue");
  });

  it("flags a due-today date as overdue too (<=0 days, not just negative)", () => {
    const { container } = render(<OrderDueDate iso="2026-06-15T00:00:00Z" status="IN_PROGRESS" />);
    expect(container.querySelector(".bang")).toBeInTheDocument();
  });

  it.each(["SHIPPED", "DELIVERED", "CANCELLED"] as const)(
    "never flags a past-due date as overdue once the order is %s",
    (status) => {
      const { container } = render(<OrderDueDate iso="2026-01-01T00:00:00Z" status={status} />);
      expect(container.querySelector(".bang")).not.toBeInTheDocument();
    },
  );

  it("still flags an in-progress order overdue, unlike the terminal statuses above", () => {
    const { container } = render(<OrderDueDate iso="2026-01-01T00:00:00Z" status="IN_PROGRESS" />);
    expect(container.querySelector(".bang")).toBeInTheDocument();
  });

  it("shows an em-dash placeholder, never flagged overdue, when there's no due date at all", () => {
    const { container } = render(<OrderDueDate iso={null} status="IN_PROGRESS" />);
    expect(screen.getByText("—")).toBeInTheDocument();
    expect(container.querySelector(".bang")).not.toBeInTheDocument();
  });
});
