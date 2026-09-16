import { api } from "../api/client";
import type { ColorwayView, CreateColorwayRequest } from "./types";

const base = (groupId: string) => `/productcatalog/groups/${groupId}`;

export const productCatalogApi = {
  colorways: (groupId: string) => api.get<ColorwayView[]>(`${base(groupId)}/colorways`),
  createColorway: (groupId: string, body: CreateColorwayRequest) =>
    api.post<ColorwayView>(`${base(groupId)}/colorways`, body),
  updateColorway: (groupId: string, colorwayId: string, body: CreateColorwayRequest) =>
    api.put<ColorwayView>(`${base(groupId)}/colorways/${colorwayId}`, body),
  promoteColorway: (groupId: string, colorwayId: string) =>
    api.post<ColorwayView>(`${base(groupId)}/colorways/${colorwayId}/promote`),
  deleteColorway: (groupId: string, colorwayId: string) =>
    api.delete<void>(`${base(groupId)}/colorways/${colorwayId}`),
};
