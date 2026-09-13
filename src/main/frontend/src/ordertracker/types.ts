export type AcquisitionChannel = "INSTAGRAM" | "WHATSAPP" | "REFERRAL" | "WORD_OF_MOUTH" | "WALK_IN" | "OTHER";
export type OrderStatus = "RECEIVED" | "IN_PROGRESS" | "READY_FOR_SHIPMENT" | "SHIPPED" | "DELIVERED" | "CANCELLED";
export type PaymentStatus = "UNPAID" | "PARTIALLY_PAID" | "PAID" | "FULLY_REFUNDED";
export type PaymentMode = "CASH" | "UPI" | "BANK_TRANSFER" | "CARD" | "OTHER";

export interface Customer {
  id: string;
  name: string;
  contactNumber: string | null;
  email: string | null;
  instagramHandle: string | null;
  acquisitionChannel: AcquisitionChannel;
  firstContactDate: string | null;
  shippingAddress: string | null;
  notes: string | null;
}

export interface Creator {
  id: string;
  groupId: string;
  userId: string;
  name: string;
  baseLocation: string;
  locationCode: string;
  creatorCode: string;
  hoursAvailablePerDay: number;
}

export interface MandatoryItemType {
  itemKey: string;
  label: string;
  allowedValues: string[] | null;
}

export interface WorkStageType {
  stageKey: string;
  label: string;
  sequenceOrder: number;
}

export interface BusinessConfig {
  groupId: string;
  overheadPercentage: number;
  profitMarginPercentage: number;
  currency: string;
  individualOrderTypeCode: string;
  bulkOrderTypeCode: string;
  mandatoryItemTypes: MandatoryItemType[];
  workStages: WorkStageType[];
}

export interface PresetOption {
  id: string;
  label: string;
  estimatedCost: number;
  estimatedTimeHours: number;
}

export interface StageProgress {
  stageKey: string;
  label: string;
  sequenceOrder: number;
  assigneeCreatorId: string | null;
  estimatedHours: number;
  completionFraction: number;
}

export interface PaymentView {
  amount: number;
  mode: PaymentMode;
  note: string | null;
  paidAt: string;
}

export interface ShipmentLegView {
  originLocationCode: string;
  destinationLocationCode: string;
  carrier: string | null;
  trackingNumber: string | null;
  estimatedCost: number;
  estimatedTimeHours: number;
  shippedAt: string | null;
  deliveredAt: string | null;
}

export interface ChangeLogEntry {
  field: string;
  oldValue: string | null;
  newValue: string | null;
  changedByUserId: string;
  changedAt: string;
}

export interface OrderView {
  id: string;
  orderNumber: string;
  customerId: string;
  primaryCreatorId: string;
  description: string | null;
  mandatoryItems: Record<string, string> | null;
  addOns: string[] | null;
  stageProgress: StageProgress[];
  overallCompletionPercent: number;
  materialsCost: number;
  packagingCost: number;
  totalCost: number;
  payments: PaymentView[];
  paymentStatus: PaymentStatus;
  status: OrderStatus;
  computedDueDate: string;
  shipmentPlan: ShipmentLegView[];
  changeLog: ChangeLogEntry[];
  createdAt: string;
  updatedAt: string;
}

export interface BulkVariant {
  label: string;
  quantity: number;
  mandatoryItems: Record<string, string> | null;
}

export interface CreatorSplit {
  creatorId: string;
  assignedQuantity: number;
  estimatedHoursPerUnit: number;
  completionFraction: number;
}

export interface BulkOrderView {
  id: string;
  orderNumber: string;
  customerId: string;
  description: string | null;
  variants: BulkVariant[];
  totalQuantity: number;
  creatorSplits: CreatorSplit[];
  overallCompletionPercent: number;
  materialsCost: number;
  packagingCost: number;
  totalCost: number;
  payments: PaymentView[];
  paymentStatus: PaymentStatus;
  status: OrderStatus;
  computedDueDate: string;
  shipmentPlan: ShipmentLegView[];
  changeLog: ChangeLogEntry[];
  createdAt: string;
  updatedAt: string;
}
