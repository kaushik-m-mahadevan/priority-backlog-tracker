import { useRef, useState } from "react";

const SWIPE_THRESHOLD_PX = 60;

/** Single-finger horizontal swipe detection for a list row/card — ui-9. Tracks the live
 *  drag distance in `offset` (for visual feedback while dragging) and fires onSwipeLeft /
 *  onSwipeRight once the finger lifts past the threshold in either direction. Uses a ref
 *  for the in-progress distance (not just the `offset` state) so onTouchEnd always reads
 *  the true final value regardless of React's event batching.
 *
 *  <p>Found in round 5 review: on a card inside a horizontally-scrollable ancestor (the
 *  Kanban board, which must scroll sideways to reach columns off-screen on a phone), the
 *  same horizontal drag that scrolls the board also accumulates as swipe distance here —
 *  scrolling to see the next column could silently also fire "cancel this order" once the
 *  finger lifted. This hook now finds the nearest horizontally-scrollable ancestor at
 *  touch-start and aborts the gesture (no callback, no visual offset) the moment that
 *  ancestor's `scrollLeft` actually moves, so a real scroll can never masquerade as a
 *  swipe. A drag that never scrolls the ancestor (there's nothing to scroll, or the touch
 *  started in a column with no overflow) behaves exactly as before. */
export function useSwipe(onSwipeLeft?: () => void, onSwipeRight?: () => void) {
  const [offset, setOffset] = useState(0);
  const startX = useRef<number | null>(null);
  const dx = useRef(0);
  const scrollParent = useRef<Element | null>(null);
  const startScrollLeft = useRef(0);

  const findScrollParent = (el: Element | null): Element | null => {
    for (let node = el; node && node !== document.body; node = node.parentElement) {
      if (node.scrollWidth > node.clientWidth) return node;
    }
    return null;
  };

  const onTouchStart = (e: React.TouchEvent) => {
    startX.current = e.touches[0].clientX;
    dx.current = 0;
    scrollParent.current = findScrollParent(e.currentTarget);
    startScrollLeft.current = scrollParent.current?.scrollLeft ?? 0;
  };

  const onTouchMove = (e: React.TouchEvent) => {
    if (startX.current === null) return;
    if (scrollParent.current && scrollParent.current.scrollLeft !== startScrollLeft.current) {
      // The ancestor actually scrolled during this touch — the user is scrolling the
      // board, not swiping this card. Abort so onTouchEnd can't fire a swipe action.
      startX.current = null;
      dx.current = 0;
      setOffset(0);
      return;
    }
    dx.current = e.touches[0].clientX - startX.current;
    setOffset(dx.current);
  };

  const onTouchEnd = () => {
    if (startX.current !== null) {
      if (dx.current <= -SWIPE_THRESHOLD_PX) onSwipeLeft?.();
      else if (dx.current >= SWIPE_THRESHOLD_PX) onSwipeRight?.();
    }
    startX.current = null;
    dx.current = 0;
    setOffset(0);
  };

  return { offset, onTouchStart, onTouchMove, onTouchEnd };
}
