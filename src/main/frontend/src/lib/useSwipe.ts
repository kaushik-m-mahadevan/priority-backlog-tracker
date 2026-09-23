import { useRef, useState } from "react";

const SWIPE_THRESHOLD_PX = 60;

/** Single-finger horizontal swipe detection for a list row/card — ui-9. Tracks the live
 *  drag distance in `offset` (for visual feedback while dragging) and fires onSwipeLeft /
 *  onSwipeRight once the finger lifts past the threshold in either direction. Uses a ref
 *  for the in-progress distance (not just the `offset` state) so onTouchEnd always reads
 *  the true final value regardless of React's event batching. */
export function useSwipe(onSwipeLeft?: () => void, onSwipeRight?: () => void) {
  const [offset, setOffset] = useState(0);
  const startX = useRef<number | null>(null);
  const dx = useRef(0);

  const onTouchStart = (e: React.TouchEvent) => {
    startX.current = e.touches[0].clientX;
    dx.current = 0;
  };

  const onTouchMove = (e: React.TouchEvent) => {
    if (startX.current === null) return;
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
