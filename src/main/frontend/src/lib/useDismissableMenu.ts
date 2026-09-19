import { useEffect, useRef, useState } from "react";

/** Shared open/close + outside-click + Escape dismissal for a header dropdown/popover
 *  (fdup-5) — previously reimplemented, slightly differently each time, in NavMenu, Bell,
 *  and Connections. Bell's own copy was missing the Escape handler and kept its listeners
 *  attached even while closed; this version fixes both, gating the listeners on `open`
 *  like NavMenu/Connections already did. */
export function useDismissableMenu<T extends HTMLElement = HTMLDivElement>() {
  const [open, setOpen] = useState(false);
  const ref = useRef<T>(null);

  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return { open, setOpen, ref };
}
