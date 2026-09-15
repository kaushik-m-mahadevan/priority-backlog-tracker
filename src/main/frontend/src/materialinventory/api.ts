import { api } from "../api/client";
import type {
  CreateTransferRequestRequest,
  CreateYarnTypeRequest,
  FulfillTransferRequest,
  InventoryEntryView,
  SetInventoryQuantityRequest,
  TransferRequestView,
  YarnTypeView,
} from "./types";

const base = (groupId: string) => `/materialinventory/groups/${groupId}`;

export const materialInventoryApi = {
  yarnTypes: (groupId: string) => api.get<YarnTypeView[]>(`${base(groupId)}/yarn-types`),
  createYarnType: (groupId: string, body: CreateYarnTypeRequest) =>
    api.post<YarnTypeView>(`${base(groupId)}/yarn-types`, body),
  updateYarnType: (groupId: string, yarnTypeId: string, body: CreateYarnTypeRequest) =>
    api.put<YarnTypeView>(`${base(groupId)}/yarn-types/${yarnTypeId}`, body),
  deleteYarnType: (groupId: string, yarnTypeId: string) =>
    api.delete<void>(`${base(groupId)}/yarn-types/${yarnTypeId}`),

  inventory: (groupId: string) => api.get<InventoryEntryView[]>(`${base(groupId)}/inventory`),
  myInventory: (groupId: string) => api.get<InventoryEntryView[]>(`${base(groupId)}/inventory/mine`),
  setMyQuantity: (groupId: string, yarnTypeId: string, body: SetInventoryQuantityRequest) =>
    api.put<void>(`${base(groupId)}/inventory/mine/${yarnTypeId}`, body),

  transfers: (groupId: string) => api.get<TransferRequestView[]>(`${base(groupId)}/transfers`),
  createTransferRequest: (groupId: string, body: CreateTransferRequestRequest) =>
    api.post<TransferRequestView>(`${base(groupId)}/transfers`, body),
  fulfillTransferRequest: (groupId: string, requestId: string, body: FulfillTransferRequest) =>
    api.post<TransferRequestView>(`${base(groupId)}/transfers/${requestId}/fulfill`, body),
  completeTransferRequest: (groupId: string, requestId: string) =>
    api.post<TransferRequestView>(`${base(groupId)}/transfers/${requestId}/complete`),
  cancelTransferRequest: (groupId: string, requestId: string) =>
    api.post<TransferRequestView>(`${base(groupId)}/transfers/${requestId}/cancel`),
};
