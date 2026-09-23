import { describe, expect, it } from "vitest";
import { renderHook } from "@testing-library/react";
import { useDisableNumberInputScroll } from "./useDisableNumberInputScroll";

describe("useDisableNumberInputScroll", () => {
  it("blurs a focused number input on wheel", () => {
    const input = document.createElement("input");
    input.type = "number";
    document.body.appendChild(input);
    input.focus();
    expect(document.activeElement).toBe(input);

    renderHook(() => useDisableNumberInputScroll());
    input.dispatchEvent(new WheelEvent("wheel", { bubbles: true }));

    expect(document.activeElement).not.toBe(input);
    document.body.removeChild(input);
  });

  it("leaves a focused text input alone on wheel", () => {
    const input = document.createElement("input");
    input.type = "text";
    document.body.appendChild(input);
    input.focus();

    renderHook(() => useDisableNumberInputScroll());
    input.dispatchEvent(new WheelEvent("wheel", { bubbles: true }));

    expect(document.activeElement).toBe(input);
    document.body.removeChild(input);
  });

  it("does nothing when no number input is focused", () => {
    renderHook(() => useDisableNumberInputScroll());
    expect(() => document.dispatchEvent(new WheelEvent("wheel"))).not.toThrow();
  });

  it("removes its listener on unmount", () => {
    const input = document.createElement("input");
    input.type = "number";
    document.body.appendChild(input);

    const { unmount } = renderHook(() => useDisableNumberInputScroll());
    unmount();

    input.focus();
    input.dispatchEvent(new WheelEvent("wheel", { bubbles: true }));
    expect(document.activeElement).toBe(input);
    document.body.removeChild(input);
  });
});
