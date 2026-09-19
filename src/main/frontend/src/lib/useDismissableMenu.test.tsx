import { act, render, renderHook, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { useDismissableMenu } from "./useDismissableMenu";

function Menu() {
  const { open, setOpen, ref } = useDismissableMenu<HTMLDivElement>();
  return (
    <div>
      <div ref={ref}>
        <button onClick={() => setOpen((o) => !o)}>Toggle</button>
        {open && <div role="menu">Contents</div>}
      </div>
      <button>Outside</button>
    </div>
  );
}

describe("useDismissableMenu", () => {
  it("starts closed", () => {
    const { result } = renderHook(() => useDismissableMenu());
    expect(result.current.open).toBe(false);
  });

  it("setOpen(true) opens it", () => {
    const { result } = renderHook(() => useDismissableMenu());
    act(() => result.current.setOpen(true));
    expect(result.current.open).toBe(true);
  });

  it("a mousedown outside the ref'd element closes it", async () => {
    const user = userEvent.setup();
    render(<Menu />);
    await user.click(screen.getByText("Toggle"));
    expect(screen.getByRole("menu")).toBeInTheDocument();

    await user.click(screen.getByText("Outside"));

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("a click inside the ref'd element does not close it", async () => {
    const user = userEvent.setup();
    render(<Menu />);
    await user.click(screen.getByText("Toggle"));
    await user.click(screen.getByRole("menu"));
    expect(screen.getByRole("menu")).toBeInTheDocument();
  });

  it("pressing Escape closes it", async () => {
    const user = userEvent.setup();
    render(<Menu />);
    await user.click(screen.getByText("Toggle"));
    expect(screen.getByRole("menu")).toBeInTheDocument();

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("does nothing on outside click or Escape while already closed", async () => {
    const user = userEvent.setup();
    render(<Menu />);
    await user.click(screen.getByText("Outside"));
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });
});
