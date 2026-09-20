import { useState, type ReactNode } from "react";

/** Collapsible top-level grouping — open by default on wide screens (so the page reads as
 *  one organized document), collapsed by default on narrow ones (so a phone isn't hit with
 *  everything at once). Originally local to OrderDetailPage; pulled out (ui-4) so
 *  NewOrderPage's own field groups can reuse the identical collapse behavior instead of
 *  every field showing at once on mobile. */
export function Section({
  title, icon, children, defaultOpen,
}: { title: string; icon: string; children: ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(() => {
    if (defaultOpen !== undefined) return defaultOpen;
    return typeof window === "undefined" ? true : window.innerWidth >= 768;
  });
  return (
    <section className="order-section">
      <button type="button" className="order-section-toggle" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        <span className="order-section-title">
          <span aria-hidden="true">{icon}</span> {title}
        </span>
        <span className="order-section-chevron" aria-hidden="true">{open ? "▾" : "▸"}</span>
      </button>
      {open && <div className="order-section-body">{children}</div>}
    </section>
  );
}
