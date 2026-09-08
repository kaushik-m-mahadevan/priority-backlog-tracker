import { useEffect, useRef } from "react";

/** Fired whenever an item is created, edited, status-changed, or completed —
    every list / badge listens and refetches. */
export const ITEMS_CHANGED = "pbt:items-changed";

export function notifyItemsChanged() {
  window.dispatchEvent(new Event(ITEMS_CHANGED));
}

export function useItemsChanged(cb: () => void) {
  const ref = useRef(cb);
  ref.current = cb;
  useEffect(() => {
    const h = () => ref.current();
    window.addEventListener(ITEMS_CHANGED, h);
    return () => window.removeEventListener(ITEMS_CHANGED, h);
  }, []);
}
