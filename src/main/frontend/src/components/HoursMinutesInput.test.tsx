import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { HoursMinutesInput } from "./HoursMinutesInput";

/** A real controlled usage (hours prop fed back from onChange) — needed for the edit
 *  tests below, since a fixed-prop render would snap the input back to its old value
 *  after every keystroke, the same as it would in a real, unwired-up controlled input. */
function Controlled({ initial, onChange }: { initial: number; onChange: (h: number) => void }) {
  const [hours, setHours] = useState(initial);
  return (
    <HoursMinutesInput
      idPrefix="t"
      hours={hours}
      onChange={(h) => {
        setHours(h);
        onChange(h);
      }}
    />
  );
}

describe("HoursMinutesInput", () => {
  it("splits a decimal-hours value into whole hours and minutes", () => {
    render(<HoursMinutesInput idPrefix="t" hours={2.5} onChange={() => {}} />);
    expect(screen.getByLabelText("Hours")).toHaveValue(2);
    expect(screen.getByLabelText("Minutes")).toHaveValue(30);
  });

  it("lets a sub-hour duration like 10 minutes be entered directly", () => {
    render(<HoursMinutesInput idPrefix="t" hours={0.1667} onChange={() => {}} />);
    expect(screen.getByLabelText("Hours")).toHaveValue(0);
    expect(screen.getByLabelText("Minutes")).toHaveValue(10);
  });

  it("combines a minutes-only edit back into decimal hours", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Controlled initial={0} onChange={onChange} />);
    const minutesInput = screen.getByLabelText("Minutes");
    await user.clear(minutesInput);
    await user.type(minutesInput, "10");
    expect(onChange).toHaveBeenLastCalledWith(10 / 60);
  });

  it("combines an hours edit, keeping the existing minutes", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Controlled initial={1.5} onChange={onChange} />);
    const hoursInput = screen.getByLabelText("Hours");
    await user.clear(hoursInput);
    await user.type(hoursInput, "3");
    expect(onChange).toHaveBeenLastCalledWith(3.5);
  });

  it("clamps minutes to 0-59", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Controlled initial={0} onChange={onChange} />);
    const minutesInput = screen.getByLabelText("Minutes");
    await user.clear(minutesInput);
    await user.type(minutesInput, "90");
    expect(onChange).toHaveBeenLastCalledWith(59 / 60);
  });

  it("treats a negative/NaN hours prop as zero", () => {
    render(<HoursMinutesInput idPrefix="t" hours={-5} onChange={() => {}} />);
    expect(screen.getByLabelText("Hours")).toHaveValue(0);
    expect(screen.getByLabelText("Minutes")).toHaveValue(0);
  });
});
