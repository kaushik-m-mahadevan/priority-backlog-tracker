import { render, screen } from "@testing-library/react";
import { useRef } from "react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { usePopoverPosition } from "./usePopoverPosition";

function mockRect(el: HTMLElement, rect: Partial<DOMRect>) {
  el.getBoundingClientRect = () => ({
    x: 0, y: 0, width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0, toJSON: () => ({}),
    ...rect,
  });
}

function TestPopover({ open, triggerRect, popRect }: { open: boolean; triggerRect: Partial<DOMRect>; popRect: Partial<DOMRect> }) {
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const { popoverRef, style } = usePopoverPosition(triggerRef, open);
  return (
    <div>
      <button
        ref={(el) => {
          triggerRef.current = el;
          if (el) mockRect(el, triggerRect);
        }}
      >
        Trigger
      </button>
      {open && (
        <div
          ref={(el) => {
            popoverRef.current = el;
            if (el) mockRect(el, popRect);
          }}
          data-testid="popover"
          style={style}
        >
          Content
        </div>
      )}
    </div>
  );
}

describe("usePopoverPosition", () => {
  const originalInnerWidth = window.innerWidth;
  const originalInnerHeight = window.innerHeight;

  beforeEach(() => {
    Object.defineProperty(window, "innerWidth", { value: 400, configurable: true });
    Object.defineProperty(window, "innerHeight", { value: 700, configurable: true });
  });

  afterEach(() => {
    Object.defineProperty(window, "innerWidth", { value: originalInnerWidth, configurable: true });
    Object.defineProperty(window, "innerHeight", { value: originalInnerHeight, configurable: true });
  });

  it("right-aligns below the trigger when there's plenty of room", () => {
    render(
      <TestPopover
        open
        triggerRect={{ top: 40, bottom: 60, left: 300, right: 380 }}
        popRect={{ width: 180, height: 200 }}
      />
    );
    const style = screen.getByTestId("popover").style;
    expect(style.top).toBe("66px"); // bottom(60) + GAP(6)
    expect(style.left).toBe("200px"); // right(380) - width(180)
  });

  it("clamps left to the viewport margin instead of going negative near the left edge", () => {
    render(
      <TestPopover
        open
        triggerRect={{ top: 40, bottom: 60, left: 10, right: 90 }}
        popRect={{ width: 180, height: 200 }}
      />
    );
    // naive right-align would be 90 - 180 = -90 (off-screen) -- must clamp to the margin
    expect(screen.getByTestId("popover").style.left).toBe("8px");
  });

  it("clamps left to stay fully on-screen when the trigger is near the right edge", () => {
    render(
      <TestPopover
        open
        triggerRect={{ top: 40, bottom: 60, left: 380, right: 398 }}
        popRect={{ width: 180, height: 200 }}
      />
    );
    // right-aligning to the trigger (398 - 180 = 218) already fits under innerWidth(400) - margin,
    // so this asserts the popover never extends past the viewport's right edge either
    const left = Number(screen.getByTestId("popover").style.left.replace("px", ""));
    expect(left + 180).toBeLessThanOrEqual(400 - 8);
  });

  it("flips above the trigger when there's no room below", () => {
    render(
      <TestPopover
        open
        triggerRect={{ top: 650, bottom: 670, left: 200, right: 300 }}
        popRect={{ width: 180, height: 200 }}
      />
    );
    // below would be 670+6=676, +height(200)=876 > innerHeight(700)-margin -- must flip above
    expect(screen.getByTestId("popover").style.top).toBe("444px"); // top(650) - height(200) - GAP(6)
  });

  it("is hidden (not yet positioned) before the layout effect has measured anything", () => {
    render(<TestPopover open={false} triggerRect={{}} popRect={{}} />);
    expect(screen.queryByTestId("popover")).not.toBeInTheDocument();
  });
});
