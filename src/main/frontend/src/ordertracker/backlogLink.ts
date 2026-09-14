import { api } from "../api/client";

/** Bridges into Priority Backlog Tracker's own REST API from Order Tracker's browser side —
 *  never backend-to-backend, per the platform's no-cross-applet-imports rule. */

export interface BacklogGroup {
  id: string;
  name: string;
  createdAt: string;
  members: { id: string; name: string; handle: string; email: string }[];
}

export interface BacklogItemView {
  id: string;
  itemId: string;
  title: string;
  category: string;
  priority: string;
}

export const backlogLinkApi = {
  myGroups: () => api.get<BacklogGroup[]>(`/groups?appletKey=backlogtracker`),
  categories: (groupId: string) => api.get<{ categories: string[] }>(`/groups/${groupId}/categories`),
  priorities: () => api.get<{ priorities: string[] }>(`/config`),
  findLinkedItem: (groupId: string, orderId: string) =>
    api.get<BacklogItemView | null>(`/items/by-linked-order/${orderId}?groupId=${groupId}`),
  createLinkedItem: (body: {
    groupId: string;
    title: string;
    category: string;
    priority: string;
    effortEstimate: { value: number; unit: "MINUTES" | "HOURS" | "DAYS" };
    dueDate: string | null;
    ownerId: string | null;
    notes: string;
    linkedOrderId: string;
  }) => api.post<BacklogItemView>(`/items`, body),
};
