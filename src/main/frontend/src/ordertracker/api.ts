import { api } from "../api/client";
import type { BusinessConfig, ChangeLog, CostConfigChangeRequest, Creator, Customer, MandatoryItemType, OrderFinalizationView, OrderView, PresetOption, WorkStageType } from "./types";

const base = (groupId: string) => `/ordertracker/groups/${groupId}`;

export const orderTrackerApi = {
  businessConfig: (groupId: string) => api.get<BusinessConfig>(`${base(groupId)}/business-config`),
  updateBusinessConfig: (
    groupId: string,
    body: { currency: string; mandatoryItemTypes: MandatoryItemType[]; workStages: WorkStageType[] }
  ) => api.put<BusinessConfig>(`${base(groupId)}/business-config`, body),

  costConfigChangeRequests: (groupId: string) =>
    api.get<CostConfigChangeRequest[]>(`${base(groupId)}/business-config/change-requests`),
  proposeCostConfigChange: (groupId: string, overheadPercentage: number, profitMarginPercentage: number) =>
    api.post<CostConfigChangeRequest>(`${base(groupId)}/business-config/change-requests`, {
      overheadPercentage,
      profitMarginPercentage,
    }),
  approveCostConfigChange: (groupId: string, requestId: string) =>
    api.post<CostConfigChangeRequest>(`${base(groupId)}/business-config/change-requests/${requestId}/approve`),
  rejectCostConfigChange: (groupId: string, requestId: string) =>
    api.post<CostConfigChangeRequest>(`${base(groupId)}/business-config/change-requests/${requestId}/reject`),

  myCreatorProfile: (groupId: string) => api.get<Creator | null>(`${base(groupId)}/creators/me`),
  upsertMyCreatorProfile: (groupId: string, body: { baseLocation: string; hoursAvailablePerDay: number }) =>
    api.put<Creator>(`${base(groupId)}/creators/me`, body),
  creators: (groupId: string) => api.get<Creator[]>(`${base(groupId)}/creators`),

  packagingPresets: (groupId: string) => api.get<PresetOption[]>(`${base(groupId)}/packaging-presets`),
  addPackagingPreset: (groupId: string, body: { label: string; estimatedCost: number; estimatedTimeHours: number }) =>
    api.post<PresetOption>(`${base(groupId)}/packaging-presets`, body),
  removePackagingPreset: (groupId: string, presetId: string) =>
    api.delete<void>(`${base(groupId)}/packaging-presets/${presetId}`),

  customers: (groupId: string) => api.get<Customer[]>(`${base(groupId)}/customers`),
  createCustomer: (groupId: string, body: Partial<Customer>) => api.post<Customer>(`${base(groupId)}/customers`, body),
  updateCustomer: (groupId: string, customerId: string, body: Partial<Customer>) =>
    api.put<Customer>(`${base(groupId)}/customers/${customerId}`, body),
  searchCustomers: (groupId: string, params: { email?: string; instagramHandle?: string; contactNumber?: string }) => {
    const query = new URLSearchParams();
    if (params.email) query.set("email", params.email);
    if (params.instagramHandle) query.set("instagramHandle", params.instagramHandle);
    if (params.contactNumber) query.set("contactNumber", params.contactNumber);
    return api.get<Customer[]>(`${base(groupId)}/customers/search?${query.toString()}`);
  },

  orders: (groupId: string) => api.get<OrderView[]>(`${base(groupId)}/orders`),
  myWork: (groupId: string, params: { status?: "pending" | "done" | "all"; from?: string; to?: string }) => {
    const query = new URLSearchParams();
    if (params.status) query.set("status", params.status);
    if (params.from) query.set("from", params.from);
    if (params.to) query.set("to", params.to);
    const qs = query.toString();
    return api.get<OrderView[]>(`${base(groupId)}/orders/my-work${qs ? `?${qs}` : ""}`);
  },
  order: (groupId: string, orderId: string) => api.get<OrderView>(`${base(groupId)}/orders/${orderId}`),
  changeLog: (groupId: string, orderId: string) => api.get<ChangeLog[]>(`${base(groupId)}/orders/${orderId}/change-log`),
  createOrder: (groupId: string, body: unknown) => api.post<OrderView>(`${base(groupId)}/orders`, body),
  updateOrder: (groupId: string, orderId: string, body: unknown) =>
    api.put<OrderView>(`${base(groupId)}/orders/${orderId}`, body),
  updateBulkDetails: (groupId: string, orderId: string, body: unknown) =>
    api.put<OrderView>(`${base(groupId)}/orders/${orderId}/bulk-details`, body),
  updateStatus: (groupId: string, orderId: string, status: string) =>
    api.patch<OrderView>(`${base(groupId)}/orders/${orderId}/status`, { status }),
  updateStageAssignment: (groupId: string, orderId: string, stageKey: string, assignedCreatorId: string | null, unitsCompleted: number) =>
    api.patch<OrderView>(`${base(groupId)}/orders/${orderId}/stage-assignments/${stageKey}`, {
      assignedCreatorId,
      unitsCompleted,
    }),
  updateBulkStageProgress: (groupId: string, orderId: string, stageKey: string, unitsCompleted: number) =>
    api.patch<OrderView>(`${base(groupId)}/orders/${orderId}/bulk-stage-progress/${stageKey}`, { unitsCompleted }),
  updateBulkSplitProgress: (
    groupId: string,
    orderId: string,
    variantId: string,
    creatorId: string,
    stageKey: string,
    unitsCompleted: number
  ) =>
    api.patch<OrderView>(`${base(groupId)}/orders/${orderId}/bulk-split-progress`, {
      variantId,
      creatorId,
      stageKey,
      unitsCompleted,
    }),
  addPayment: (groupId: string, orderId: string, body: unknown) =>
    api.post<OrderView>(`${base(groupId)}/orders/${orderId}/payments`, body),
  setShipmentPlan: (groupId: string, orderId: string, stops: unknown[]) =>
    api.put<OrderView>(`${base(groupId)}/orders/${orderId}/shipment-plan`, { stops }),
  markShipmentStop: (groupId: string, orderId: string, stopIndex: number, body: { shippedDate?: string; deliveredConfirmed?: boolean }) =>
    api.patch<OrderView>(`${base(groupId)}/orders/${orderId}/shipment-plan/${stopIndex}`, body),

  orderFinalization: (groupId: string, orderId: string) =>
    api.get<OrderFinalizationView>(`${base(groupId)}/orders/${orderId}/finalization`),
  proposeOrderFinalization: (groupId: string, orderId: string, finalCost: number, finalRevenue: number) =>
    api.post<OrderFinalizationView>(`${base(groupId)}/orders/${orderId}/finalization/propose`, { finalCost, finalRevenue }),
  approveOrderFinalization: (groupId: string, orderId: string) =>
    api.post<OrderFinalizationView>(`${base(groupId)}/orders/${orderId}/finalization/approve`),
  rejectOrderFinalization: (groupId: string, orderId: string) =>
    api.post<OrderFinalizationView>(`${base(groupId)}/orders/${orderId}/finalization/reject`),
};
