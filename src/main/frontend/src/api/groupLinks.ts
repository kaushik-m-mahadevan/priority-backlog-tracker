import { api } from "./client";
import type { GroupLinkProposalView, GroupView } from "../types";

/** Thin, shared wrapper over the generic commons cross-applet linking endpoints
 *  (`/api/groups/{id}/links...`) — previously reimplemented inline, once each, in Order
 *  Tracker's and Finance Tracker's own Manage pages with a hardcoded "other applet key"
 *  baked into each copy. Pulled out so the shared Connections header widget (and those two
 *  pages, if they're ever revisited) can call one function instead of duplicating the raw
 *  path strings again for every new applet pairing. */
export const groupLinkApi = {
  /** The group linked to `groupId` for `otherAppletKey`, if any. */
  linkedGroupId: (groupId: string, otherAppletKey: string) =>
    api.get<{ linkedGroupId: string | null }>(`/groups/${groupId}/links/${otherAppletKey}`).then((r) => r.linkedGroupId),

  /** Every group in `appletKey` that the current user belongs to — candidates to link to. */
  myGroupsIn: (appletKey: string) => api.get<GroupView[]>(`/groups?appletKey=${appletKey}`),

  /** Full member list for `groupId` itself — used to compute the mb-23 invite-delta preview
   *  against a candidate group's own members. */
  group: (groupId: string) => api.get<GroupView>(`/groups/${groupId}`),

  unlink: (groupId: string, otherAppletKey: string) => api.delete<void>(`/groups/${groupId}/links/${otherAppletKey}`),

  invite: (groupId: string, to: string) => api.post<void>(`/groups/${groupId}/invites`, { to }),

  /** mb-14: the manual link path — gated behind unanimous approval from `groupId`'s own
   *  members, rather than linking outright like the Setup Wizard's own raw link call. The
   *  returned proposal's `status` is `APPROVED` immediately for a solo-member group (the
   *  same "lone proposer already satisfies unanimity" rule every other approval flow has),
   *  otherwise `PENDING` until every other member approves. */
  proposeLink: (groupId: string, targetGroupId: string, intoCurrentEmails: string[], intoTargetEmails: string[]) =>
    api.post<GroupLinkProposalView>(`/groups/${groupId}/link-proposals`, {
      groupId: targetGroupId, intoCurrentEmails, intoTargetEmails,
    }),

  linkProposals: (groupId: string) => api.get<GroupLinkProposalView[]>(`/groups/${groupId}/link-proposals`),

  approveLinkProposal: (groupId: string, requestId: string) =>
    api.post<GroupLinkProposalView>(`/groups/${groupId}/link-proposals/${requestId}/approve`),

  rejectLinkProposal: (groupId: string, requestId: string) =>
    api.post<GroupLinkProposalView>(`/groups/${groupId}/link-proposals/${requestId}/reject`),
};
