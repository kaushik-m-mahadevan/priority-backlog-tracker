import { api } from "../api/client";
import type {
  CreateNeedleTypeRequest,
  CreateTransferRequestRequest,
  CreateYarnTypeRequest,
  FulfillTransferRequest,
  InventoryEntryView,
  NeedleInventoryEntryView,
  NeedleTypeView,
  SetInventoryQuantityRequest,
  SetNeedleQuantityRequest,
  TransferRequestView,
  TransferSuggestionView,
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
  transferSuggestions: (groupId: string, yarnTypeId: string) =>
    api.get<TransferSuggestionView[]>(`${base(groupId)}/inventory/transfer-suggestions/${yarnTypeId}`),

  transfers: (groupId: string) => api.get<TransferRequestView[]>(`${base(groupId)}/transfers`),
  createTransferRequest: (groupId: string, body: CreateTransferRequestRequest) =>
    api.post<TransferRequestView>(`${base(groupId)}/transfers`, body),
  fulfillTransferRequest: (groupId: string, requestId: string, body: FulfillTransferRequest) =>
    api.post<TransferRequestView>(`${base(groupId)}/transfers/${requestId}/fulfill`, body),
  completeTransferRequest: (groupId: string, requestId: string) =>
    api.post<TransferRequestView>(`${base(groupId)}/transfers/${requestId}/complete`),
  cancelTransferRequest: (groupId: string, requestId: string) =>
    api.post<TransferRequestView>(`${base(groupId)}/transfers/${requestId}/cancel`),

  needleTypes: (groupId: string) => api.get<NeedleTypeView[]>(`${base(groupId)}/needle-types`),
  createNeedleType: (groupId: string, body: CreateNeedleTypeRequest) =>
    api.post<NeedleTypeView>(`${base(groupId)}/needle-types`, body),
  updateNeedleType: (groupId: string, needleTypeId: string, body: CreateNeedleTypeRequest) =>
    api.put<NeedleTypeView>(`${base(groupId)}/needle-types/${needleTypeId}`, body),
  deleteNeedleType: (groupId: string, needleTypeId: string) =>
    api.delete<void>(`${base(groupId)}/needle-types/${needleTypeId}`),

  needleInventory: (groupId: string) => api.get<NeedleInventoryEntryView[]>(`${base(groupId)}/needle-inventory`),
  myNeedleInventory: (groupId: string) => api.get<NeedleInventoryEntryView[]>(`${base(groupId)}/needle-inventory/mine`),
  setMyNeedleQuantity: (groupId: string, needleTypeId: string, body: SetNeedleQuantityRequest) =>
    api.put<void>(`${base(groupId)}/needle-inventory/mine/${needleTypeId}`, body),
};
