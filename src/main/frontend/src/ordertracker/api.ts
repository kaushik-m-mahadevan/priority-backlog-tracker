import { api } from "../api/client";
import type {
  BulkOrderView,
  BusinessConfig,
  Creator,
  Customer,
  OrderView,
  PresetOption,
} from "./types";

const base = (groupId: string) => `/ordertracker/groups/${groupId}`;

export const orderTrackerApi = {
  businessConfig: (groupId: string) => api.get<BusinessConfig>(`${base(groupId)}/business-config`),
  updateBusinessConfig: (groupId: string, body: Omit<BusinessConfig, "groupId" | "individualOrderTypeCode" | "bulkOrderTypeCode">) =>
    api.put<BusinessConfig>(`${base(groupId)}/business-config`, body),

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

  orders: (groupId: string) => api.get<OrderView[]>(`${base(groupId)}/orders`),
  order: (groupId: string, orderId: string) => api.get<OrderView>(`${base(groupId)}/orders/${orderId}`),
  createOrder: (groupId: string, body: unknown) => api.post<OrderView>(`${base(groupId)}/orders`, body),
  updateOrderStage: (groupId: string, orderId: string, stageKey: string, completionFraction: number) =>
    api.patch<OrderView>(`${base(groupId)}/orders/${orderId}/stages/${stageKey}`, { completionFraction }),
  addOrderPayment: (groupId: string, orderId: string, body: unknown) =>
    api.post<OrderView>(`${base(groupId)}/orders/${orderId}/payments`, body),
  updateOrderStatus: (groupId: string, orderId: string, status: string) =>
    api.patch<OrderView>(`${base(groupId)}/orders/${orderId}/status`, { status }),
  setShipmentPlan: (groupId: string, orderId: string, legs: unknown[]) =>
    api.post<OrderView>(`${base(groupId)}/orders/${orderId}/shipment-plan`, legs),
  markShipmentLeg: (groupId: string, orderId: string, legIndex: number, body: { shippedAt?: string; deliveredAt?: string }) =>
    api.patch<OrderView>(`${base(groupId)}/orders/${orderId}/shipment-plan/${legIndex}`, body),

  bulkOrders: (groupId: string) => api.get<BulkOrderView[]>(`${base(groupId)}/bulk-orders`),
  createBulkOrder: (groupId: string, body: unknown) => api.post<BulkOrderView>(`${base(groupId)}/bulk-orders`, body),
  updateCreatorSplit: (groupId: string, orderId: string, creatorId: string, completionFraction: number) =>
    api.patch<BulkOrderView>(`${base(groupId)}/bulk-orders/${orderId}/creator-splits/${creatorId}`, { completionFraction }),
  addBulkOrderPayment: (groupId: string, orderId: string, body: unknown) =>
    api.post<BulkOrderView>(`${base(groupId)}/bulk-orders/${orderId}/payments`, body),
};
