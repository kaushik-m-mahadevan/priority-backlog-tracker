import { useEffect } from "react";

/** mb-7: scrolling over a focused number input silently changes its value (a long-
 *  standing browser default, not something this app opted into) — one global listener
 *  blurs whichever number input is focused the instant a wheel event reaches it, so the
 *  page scrolls normally instead. Cheaper and more reliable than an onWheel handler on
 *  every one of this app's many number inputs. */
export function useDisableNumberInputScroll() {
  useEffect(() => {
    const onWheel = () => {
      const active = document.activeElement;
      if (active instanceof HTMLInputElement && active.type === "number") {
        active.blur();
      }
    };
    document.addEventListener("wheel", onWheel, { passive: true });
    return () => document.removeEventListener("wheel", onWheel);
  }, []);
}
