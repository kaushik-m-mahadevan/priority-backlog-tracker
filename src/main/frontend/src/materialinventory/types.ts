export interface YarnCostChange {
  previousCost: number | null;
  newCost: number | null;
  changedAt: string;
}

export interface YarnTypeView {
  id: string;
  brand: string;
  thickness: string;
  colour: string;
  material: string | null;
  skeinWeightGrams: number | null;
  skeinLengthMeters: number | null;
  recommendedHookSize: string | null;
  notes: string | null;
  costPerSkein: number | null;
  costHistory: YarnCostChange[];
}

export interface CreateYarnTypeRequest {
  brand: string;
  thickness: string;
  colour: string;
  material: string | null;
  skeinWeightGrams: number | null;
  skeinLengthMeters: number | null;
  recommendedHookSize: string | null;
  notes: string | null;
  costPerSkein: number | null;
}

export type NeedleKind = "CROCHET_HOOK" | "KNITTING_NEEDLE";

export interface NeedleTypeView {
  id: string;
  kind: NeedleKind;
  size: string;
  notes: string | null;
}

export interface CreateNeedleTypeRequest {
  kind: NeedleKind;
  size: string;
  notes: string | null;
}

export interface NeedleInventoryEntryView {
  id: string;
  userId: string;
  needleTypeId: string;
  quantity: number;
  updatedAt: string;
}

export interface SetNeedleQuantityRequest {
  quantity: number;
}

export interface InventoryEntryView {
  id: string;
  userId: string;
  yarnTypeId: string;
  quantity: number;
  updatedAt: string;
  /** Approximates staleness from how long this entry has gone untouched (currently 30
   *  days) — not a real activity signal, just a hint the count might be out of date. */
  stale: boolean;
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
