import type { Creator } from "../../types";

/** Shared byte-for-byte (fdup-1) across every extracted order-detail section that needs to
 *  turn a creatorId into a display name — small enough that duplicating it once more
 *  wouldn't have been unreasonable, but it's used in five places now, past the point where
 *  that's still true. */
export function creatorName(creators: Creator[], id: string | null | undefined) {
  if (!id) return <span className="muted">Unassigned</span>;
  return creators.find((c) => c.id === id)?.name ?? <span className="muted">Unknown creator</span>;
}
