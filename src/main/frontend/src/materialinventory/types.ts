export interface YarnTypeView {
  id: string;
  brand: string;
  thickness: string;
  colour: string;
  notes: string | null;
}

export interface CreateYarnTypeRequest {
  brand: string;
  thickness: string;
  colour: string;
  notes: string | null;
}

export interface InventoryEntryView {
  id: string;
  userId: string;
  yarnTypeId: string;
  quantity: number;
  updatedAt: string;
}

export interface SetInventoryQuantityRequest {
  quantity: number;
}

export type TransferStatus = "PENDING" | "PARTIALLY_FULFILLED" | "COMPLETED" | "CANCELLED";

export interface TransferRequestView {
  id: string;
  requesterId: string;
  targetUserId: string;
  yarnTypeId: string;
  requestedQuantity: number;
  fulfilledQuantity: number;
  status: TransferStatus;
  createdAt: string;
  resolvedAt: string | null;
}

export interface CreateTransferRequestRequest {
  targetUserId: string;
  yarnTypeId: string;
  requestedQuantity: number;
}

export interface FulfillTransferRequest {
  quantity: number;
}
