import type { ReactNode } from "react";

/** Shared loading -> empty -> content branching (fdup-9) — the same three-way ternary
 *  ("Loading…" / an empty-state / the real content) was hand-rolled slightly differently
 *  across ~25 pages. Error display is deliberately NOT folded in here: pages vary on
 *  whether an error replaces the content or sits alongside stale data, so each page keeps
 *  rendering its own error banner exactly where it already does. */
export function AsyncSection({
  loading,
  isEmpty,
  empty,
  children,
}: {
  loading: boolean;
  isEmpty: boolean;
  /** Rendered in place of children when not loading but isEmpty — typically either
   *  `<EmptyLeaf message="..."/>` or `<p className="empty">...</p>`, the caller's choice. */
  empty: ReactNode;
  children: ReactNode;
}) {
  if (loading) return <p className="muted">Loading…</p>;
  if (isEmpty) return <>{empty}</>;
  return <>{children}</>;
}
