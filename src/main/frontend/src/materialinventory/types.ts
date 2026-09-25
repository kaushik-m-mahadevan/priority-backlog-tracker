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
  /** ad-2: from the linked Order Tracker business's open orders (0 if nothing's linked or
   *  nothing's reserved). */
  reserved: number;
  available: number;
  /** ad-2: this owner's own stash of this yarn type is at/below the low-stock threshold. */
  personalLow: boolean;
  /** ad-2: the business-wide total (across every member) is at/below the threshold. */
  businessLow: boolean;
}

export interface SetInventoryQuantityRequest {
  quantity: number;
}

export interface TransferSuggestionView {
  userId: string;
  quantity: number;
}

export type LineStatus = "OPEN" | "CLOSED";

export interface ShipmentView {
  shipmentId: string;
  quantity: number;
  notes: string | null;
  sentAt: string;
  /** Null until the requester confirms this specific shipment physically arrived. */
  receivedAt: string | null;
}

export interface TransferLineView {
  lineId: string;
  yarnTypeId: string;
  requestedQuantity: number;
  status: LineStatus;
  /** Sum of every shipment's quantity on this line, sent or not yet confirmed. */
  sentQuantity: number;
  /** Sum of only the shipments the requester has confirmed received so far. */
  receivedQuantity: number;
  shipments: ShipmentView[];
}

export interface TransferRequestView {
  id: string;
  requesterId: string;
  targetUserId: string;
  lines: TransferLineView[];
  createdAt: string;
}

export interface CreateTransferRequestLineInput {
  yarnTypeId: string;
  quantity: number;
}

export interface CreateTransferRequestRequest {
  targetUserId: string;
  lines: CreateTransferRequestLineInput[];
}

export interface SendShipmentRequest {
  quantity: number;
  notes: string | null;
}

export type MaterialAssignmentStatus = "PENDING" | "ACCEPTED" | "REJECTED" | "CANCELLED";

/** mb-21: a proposed yarn hand-off from one member to another, gated by propose/accept —
 *  quantity is already debited from proposerId's on-hand the moment this is PENDING. */
export interface MaterialAssignmentView {
  id: string;
  proposerId: string;
  recipientId: string;
  yarnTypeId: string;
  quantity: number;
  status: MaterialAssignmentStatus;
  createdAt: string;
  resolvedAt: string | null;
}

export interface CreateMaterialAssignmentRequest {
  recipientId: string;
  yarnTypeId: string;
  quantity: number;
}
