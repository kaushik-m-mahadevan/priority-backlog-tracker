import { useState } from "react";
import { ApiError } from "../api/client";
import { groupLinkApi } from "../api/groupLinks";
import type { GroupView, UserSummary } from "../types";

export interface LinkPreview {
  /** The candidate group's members not yet in the current group — suggested invites into
   *  the current group. */
  intoCurrent: UserSummary[];
  /** The current group's members not yet in the candidate group — suggested invites into
   *  the group being linked. */
  intoTarget: UserSummary[];
  checked: Record<string, boolean>;
}

export const missingFrom = (source: UserSummary[], target: UserSummary[]): UserSummary[] => {
  const targetIds = new Set(target.map((m) => m.id));
  return source.filter((m) => !targetIds.has(m.id));
};

/** mb-23: the shared "review a two-way invite delta before linking" flow — every business
 *  member not yet in the group being linked, and every member of that group not yet in the
 *  business, suggested but freely uncheckable, so nothing invites anyone the linker didn't
 *  actually mean to. Shared by the Connections header widget and each applet's own Manage
 *  page link action, so this behaves identically wherever a link can be made rather than
 *  only wherever it was implemented first. */
export function useLinkInvitePreview(groupId: string | null) {
  const [preview, setPreview] = useState<LinkPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const review = (currentMembers: UserSummary[], target: GroupView) => {
    const intoCurrent = missingFrom(target.members, currentMembers);
    const intoTarget = missingFrom(currentMembers, target.members);
    const checked: Record<string, boolean> = {};
    [...intoCurrent, ...intoTarget].forEach((m) => (checked[m.id] = true));
    setError(null);
    setPreview({ intoCurrent, intoTarget, checked });
  };

  const toggle = (personId: string) => {
    setPreview((p) => (p ? { ...p, checked: { ...p.checked, [personId]: !p.checked[personId] } } : p));
  };

  const cancel = () => setPreview(null);

  const confirm = async (targetGroupId: string, onLinked: () => void) => {
    if (!groupId || !preview) return;
    setBusy(true);
    setError(null);
    try {
      await groupLinkApi.link(groupId, targetGroupId);
      const invites = [
        ...preview.intoCurrent.filter((m) => preview.checked[m.id]).map((m) => groupLinkApi.invite(groupId, m.email)),
        ...preview.intoTarget.filter((m) => preview.checked[m.id]).map((m) => groupLinkApi.invite(targetGroupId, m.email)),
      ];
      // Best-effort: one invite failing (already pending, group at capacity) shouldn't
      // undo the link itself, which already succeeded.
      await Promise.allSettled(invites);
      setPreview(null);
      onLinked();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not link");
    } finally {
      setBusy(false);
    }
  };

  return { preview, busy, error, setError, review, toggle, cancel, confirm };
}
