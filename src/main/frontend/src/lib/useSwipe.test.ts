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

  /** Round 5 review: a card inside a horizontally-scrollable ancestor (the Kanban board)
   *  used to fire a swipe callback even when the drag was actually just scrolling the
   *  board to reach an off-screen column. Builds one shared scrollable ancestor + card so
   *  the hook's ref (captured at touch-start) points at the same element touch-move later
   *  mutates — a fresh element per call would never look like "the same ancestor scrolled". */
  function cardInScrollableAncestor() {
    const scrollParent = document.createElement("div");
    Object.defineProperty(scrollParent, "scrollWidth", { value: 1000, configurable: true });
    Object.defineProperty(scrollParent, "clientWidth", { value: 300, configurable: true });
    let scrollLeft = 0;
    Object.defineProperty(scrollParent, "scrollLeft", {
      get: () => scrollLeft, set: (v) => { scrollLeft = v; }, configurable: true,
    });
    const card = document.createElement("div");
    scrollParent.appendChild(card);
    document.body.appendChild(scrollParent);
    return { card, setScrollLeft: (v: number) => { scrollLeft = v; } };
  }
  function touchOn(card: Element, clientX: number): React.TouchEvent {
    return { touches: [{ clientX }], currentTarget: card } as unknown as React.TouchEvent;
  }

  it("aborts the gesture (no offset, no callback) once the scrollable ancestor actually scrolls", () => {
    const onSwipeLeft = vi.fn();
    const onSwipeRight = vi.fn();
    const { card, setScrollLeft } = cardInScrollableAncestor();
    const { result } = renderHook(() => useSwipe(onSwipeLeft, onSwipeRight));
    act(() => result.current.onTouchStart(touchOn(card, 200)));
    setScrollLeft(40); // the board actually scrolled between start and this move
    act(() => result.current.onTouchMove(touchOn(card, 120)));
    expect(result.current.offset).toBe(0);
    act(() => result.current.onTouchEnd());
    expect(onSwipeLeft).not.toHaveBeenCalled();
    expect(onSwipeRight).not.toHaveBeenCalled();
  });

  it("still swipes normally when the scrollable ancestor's scrollLeft never moves", () => {
    const onSwipeLeft = vi.fn();
    const { card } = cardInScrollableAncestor();
    const { result } = renderHook(() => useSwipe(onSwipeLeft));
    act(() => result.current.onTouchStart(touchOn(card, 200)));
    act(() => result.current.onTouchMove(touchOn(card, 120)));
    act(() => result.current.onTouchEnd());
    expect(onSwipeLeft).toHaveBeenCalledTimes(1);
  });
});
