import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useSwipe } from "./useSwipe";

function touch(clientX: number): React.TouchEvent {
  return { touches: [{ clientX }] } as unknown as React.TouchEvent;
}

describe("useSwipe", () => {
  it("starts with a zero offset", () => {
    const { result } = renderHook(() => useSwipe());
    expect(result.current.offset).toBe(0);
  });

  it("tracks live drag distance as the finger moves", () => {
    const { result } = renderHook(() => useSwipe());
    act(() => result.current.onTouchStart(touch(100)));
    act(() => result.current.onTouchMove(touch(130)));
    expect(result.current.offset).toBe(30);
  });

  it("fires onSwipeLeft when released past the threshold to the left", () => {
    const onSwipeLeft = vi.fn();
    const onSwipeRight = vi.fn();
    const { result } = renderHook(() => useSwipe(onSwipeLeft, onSwipeRight));
    act(() => result.current.onTouchStart(touch(200)));
    act(() => result.current.onTouchMove(touch(120)));
    act(() => result.current.onTouchEnd());
    expect(onSwipeLeft).toHaveBeenCalledTimes(1);
    expect(onSwipeRight).not.toHaveBeenCalled();
    expect(result.current.offset).toBe(0);
  });

  it("fires onSwipeRight when released past the threshold to the right", () => {
    const onSwipeLeft = vi.fn();
    const onSwipeRight = vi.fn();
    const { result } = renderHook(() => useSwipe(onSwipeLeft, onSwipeRight));
    act(() => result.current.onTouchStart(touch(100)));
    act(() => result.current.onTouchMove(touch(180)));
    act(() => result.current.onTouchEnd());
    expect(onSwipeRight).toHaveBeenCalledTimes(1);
    expect(onSwipeLeft).not.toHaveBeenCalled();
  });

  it("does not fire either callback for a short drag under the threshold", () => {
    const onSwipeLeft = vi.fn();
    const onSwipeRight = vi.fn();
    const { result } = renderHook(() => useSwipe(onSwipeLeft, onSwipeRight));
    act(() => result.current.onTouchStart(touch(100)));
    act(() => result.current.onTouchMove(touch(120)));
    act(() => result.current.onTouchEnd());
    expect(onSwipeLeft).not.toHaveBeenCalled();
    expect(onSwipeRight).not.toHaveBeenCalled();
  });

  it("resets the offset back to zero after release", () => {
    const { result } = renderHook(() => useSwipe());
    act(() => result.current.onTouchStart(touch(100)));
    act(() => result.current.onTouchMove(touch(150)));
    act(() => result.current.onTouchEnd());
    expect(result.current.offset).toBe(0);
  });
});
