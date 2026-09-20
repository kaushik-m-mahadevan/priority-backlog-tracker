import { useLayoutEffect, useRef, useState, type CSSProperties, type RefObject } from "react";

const VIEWPORT_MARGIN = 8;
const GAP = 6;

/** Edge-aware popover positioning (ui-8) — computes a `position: fixed` box anchored
 *  below (or, if there's no room, above) `triggerRef`'s element, clamped so it never
 *  renders partially off-screen near a viewport edge. Shared by every header
 *  dropdown/popover (NavMenu, Bell, Connections) instead of each hardcoding
 *  `position: absolute; right: 0`, which only stays on-screen when the trigger happens
 *  to sit far enough from the left edge.
 *
 *  Stays a plain sibling in the DOM (no portal) so an existing outside-click/Escape
 *  dismissal hook (e.g. useDismissableMenu) watching an ancestor container keeps working
 *  unchanged — `position: fixed` already escapes any ancestor's layout/clipping without
 *  needing to leave the DOM subtree. */
export function usePopoverPosition(triggerRef: RefObject<HTMLElement | null>, open: boolean) {
  const popoverRef = useRef<HTMLDivElement | null>(null);
  const [style, setStyle] = useState<CSSProperties>({ position: "fixed", visibility: "hidden" });

  useLayoutEffect(() => {
    if (!open) return;
    const trigger = triggerRef.current;
    const popover = popoverRef.current;
    if (!trigger || !popover) return;

    const triggerRect = trigger.getBoundingClientRect();
    const popRect = popover.getBoundingClientRect();

    let left = triggerRect.right - popRect.width; // right-align to the trigger by default
    left = Math.min(left, window.innerWidth - popRect.width - VIEWPORT_MARGIN);
    left = Math.max(left, VIEWPORT_MARGIN);

    let top = triggerRect.bottom + GAP;
    if (top + popRect.height > window.innerHeight - VIEWPORT_MARGIN) {
      const above = triggerRect.top - popRect.height - GAP;
      top = Math.max(above, VIEWPORT_MARGIN);
    }

    // right: "auto" cancels out any `right: 0` the component's own stylesheet rule sets
    // (e.g. `.navmenu-pop`/`.popover`'s CSS-only fallback) — with both left and right set
    // on a fixed-position box, the browser stretches width to fit between them instead of
    // respecting the element's natural width, which isn't what a computed left wants here.
    setStyle({ position: "fixed", top, left, right: "auto", visibility: "visible" });
  }, [open, triggerRef]);

  return { popoverRef, style };
}
