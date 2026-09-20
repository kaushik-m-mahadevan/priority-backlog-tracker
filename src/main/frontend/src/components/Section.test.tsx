import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { Section } from "./Section";

describe("Section", () => {
  const originalInnerWidth = window.innerWidth;

  afterEach(() => {
    Object.defineProperty(window, "innerWidth", { value: originalInnerWidth, configurable: true });
  });

  it("respects an explicit defaultOpen regardless of viewport width", () => {
    Object.defineProperty(window, "innerWidth", { value: 375, configurable: true });
    render(<Section title="Summary" icon="📋" defaultOpen><p>Contents</p></Section>);
    expect(screen.getByText("Contents")).toBeInTheDocument();
  });

  it("defaults open on a wide (desktop) viewport with no explicit defaultOpen", () => {
    Object.defineProperty(window, "innerWidth", { value: 1024, configurable: true });
    render(<Section title="Summary" icon="📋"><p>Contents</p></Section>);
    expect(screen.getByText("Contents")).toBeInTheDocument();
  });

  it("defaults collapsed on a narrow (mobile) viewport with no explicit defaultOpen", () => {
    Object.defineProperty(window, "innerWidth", { value: 375, configurable: true });
    render(<Section title="Summary" icon="📋"><p>Contents</p></Section>);
    expect(screen.queryByText("Contents")).not.toBeInTheDocument();
  });

  it("toggles open/closed on click, flipping the chevron", async () => {
    const user = userEvent.setup();
    render(<Section title="Summary" icon="📋" defaultOpen={false}><p>Contents</p></Section>);
    const toggle = screen.getByRole("button", { name: /Summary/ });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("Contents")).not.toBeInTheDocument();

    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("Contents")).toBeInTheDocument();

    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("Contents")).not.toBeInTheDocument();
  });
});
