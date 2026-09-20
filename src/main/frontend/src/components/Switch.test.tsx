import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Switch } from "./Switch";

describe("Switch", () => {
  it("renders as a role=switch input reflecting the checked state", () => {
    render(<Switch checked={false} onChange={() => {}} label="Notifications" />);
    const toggle = screen.getByRole("switch", { name: "Notifications" });
    expect(toggle).not.toBeChecked();
  });

  it("calls onChange with the new value on click", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Switch checked={false} onChange={onChange} label="Notifications" />);
    await user.click(screen.getByRole("switch", { name: "Notifications" }));
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it("shows the one-line description inline but keeps help text hidden until the ? is clicked", async () => {
    const user = userEvent.setup();
    render(
      <Switch
        checked
        onChange={() => {}}
        label="Grove animations"
        description="Reacts to your backlog."
        help="A much longer explanation of exactly how the grove reacts over time."
      />,
    );
    expect(screen.getByText("Reacts to your backlog.")).toBeInTheDocument();
    expect(screen.queryByText(/much longer explanation/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /More about Grove animations/ }));
    expect(screen.getByText(/much longer explanation/)).toBeInTheDocument();
  });

  it("does not fire onChange when disabled", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Switch checked={false} onChange={onChange} label="Locked" disabled />);
    await user.click(screen.getByRole("switch", { name: "Locked" }));
    expect(onChange).not.toHaveBeenCalled();
  });
});
