import { useState } from "react";
import { ApiError } from "../api/client";
import { groupLinkApi } from "../api/groupLinks";
import type { GroupLinkProposalView, GroupView, UserSummary } from "../types";

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
  const [pending, setPending] = useState<GroupLinkProposalView | null>(null);

  /** Loads whatever PENDING link proposal (if any) this group currently has — call on
   *  mount so a member other than the proposer sees it and can approve/reject too, not
   *  just right after proposing it themselves. */
  const loadPending = async () => {
    if (!groupId) return;
    const proposals = await groupLinkApi.linkProposals(groupId);
    setPending(proposals.find((p) => p.status === "PENDING") ?? null);
  };

  const respond = async (approve: boolean) => {
    if (!groupId || !pending) return;
    setBusy(true);
    setError(null);
    try {
      const updated = approve
        ? await groupLinkApi.approveLinkProposal(groupId, pending.id)
        : await groupLinkApi.rejectLinkProposal(groupId, pending.id);
      setPending(updated.status === "PENDING" ? updated : null);
      return updated;
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not respond to the proposal");
    } finally {
      setBusy(false);
    }
  };

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

  /** mb-14: proposes the link (gated behind unanimous approval from the current group's own
   *  members) rather than linking outright — a solo-member group still resolves
   *  immediately (same rule every other approval flow has), which `onLinked` handles;
   *  otherwise `onProposed` handles the "waiting for approval" state. The checked invite
   *  emails travel with the proposal and only actually send once it's approved. */
  const confirm = async (targetGroupId: string, onLinked: () => void, onProposed: (proposal: GroupLinkProposalView) => void) => {
    if (!groupId || !preview) return;
    setBusy(true);
    setError(null);
    try {
      const intoCurrentEmails = preview.intoCurrent.filter((m) => preview.checked[m.id]).map((m) => m.email);
      const intoTargetEmails = preview.intoTarget.filter((m) => preview.checked[m.id]).map((m) => m.email);
      const proposal = await groupLinkApi.proposeLink(groupId, targetGroupId, intoCurrentEmails, intoTargetEmails);
      setPreview(null);
      if (proposal.status === "APPROVED") {
        onLinked();
      } else {
        setPending(proposal);
        onProposed(proposal);
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not propose the link");
    } finally {
      setBusy(false);
    }
  };

  return { preview, busy, error, setError, review, toggle, cancel, confirm, pending, loadPending, respond };
}
