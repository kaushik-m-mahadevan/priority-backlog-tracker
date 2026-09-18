import { api } from "./client";
import type { GroupView } from "../types";

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

  link: (groupId: string, otherGroupId: string) => api.post<void>(`/groups/${groupId}/links`, { groupId: otherGroupId }),

  unlink: (groupId: string, otherAppletKey: string) => api.delete<void>(`/groups/${groupId}/links/${otherAppletKey}`),
};
