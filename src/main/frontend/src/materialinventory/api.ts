import { api } from "../api/client";
import type { CreateYarnTypeRequest, InventoryEntryView, SetInventoryQuantityRequest, YarnTypeView } from "./types";

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
};
