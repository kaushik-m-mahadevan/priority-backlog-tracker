export type AcquisitionChannel = "INSTAGRAM" | "WHATSAPP" | "REFERRAL" | "WORD_OF_MOUTH" | "WALK_IN" | "OTHER";
export type OrderType = "INDIVIDUAL" | "BULK";
export type OrderStatus =
  | "INQUIRY"
  | "CONFIRMED"
  | "IN_PROGRESS"
  | "READY_TO_SHIP"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCELLED";
export type PaymentStatus = "UNPAID" | "PARTIALLY_PAID" | "PAID_IN_FULL" | "REFUNDED";
export type PaymentType = "ADVANCE" | "INSTALLMENT" | "FINAL" | "REFUND";
export type PatternType = "TEMPLATE" | "CUSTOM";
export type ResearchItemType = "VIDEO" | "LINK" | "IMAGE" | "NOTE";
export type ShipmentStopType = "INTERNAL_TRANSFER" | "FINAL_DELIVERY";

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
  isTool: boolean;
}

export interface WorkStageType {
  stageKey: string;
  label: string;
  sequenceOrder: number;
  splitTracked: boolean;
}

export type CostConfigChangeStatus = "PENDING" | "APPROVED" | "REJECTED" | "INVALIDATED";

export interface CostConfigChangeRequest {
  id: string;
  groupId: string;
  proposedOverheadPercentage: number;
  proposedProfitMarginPercentage: number;
  proposedByUserId: string;
  approvedByUserIds: string[];
  status: CostConfigChangeStatus;
  rejectedByUserId: string | null;
  createdAt: string;
  resolvedAt: string | null;
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

export interface Pattern {
  patternType: PatternType | null;
  templateName: string | null;
  customPatternNotes: string | null;
  attachmentUrls: string[];
}

export interface ResearchItem {
  type: ResearchItemType;
  url: string | null;
  description: string | null;
}

export interface MandatoryItem {
  itemKey: string;
  value: string;
  quantity: number;
  unitCost: number;
  notes: string | null;
}

export interface Tool {
  itemKey: string;
  value: string;
  notes: string | null;
}

export interface LineItem {
  name: string;
  category: string | null;
  attributes: Record<string, string>;
  quantity: number;
  unitCost: number;
  unitTimeHours: number | null;
  note: string | null;
}

export interface Packaging {
  tentativePresetId: string | null;
  cost: number;
  timeHours: number;
  itemizedList: LineItem[];
}

export interface BreakdownLine {
  label: string;
  amount: number;
}

export interface CostEstimate {
  mandatoryItemsCost: number;
  addOnsCost: number;
  packagingCost: number;
  grossCost: number;
  overheadAmount: number;
  profitAmount: number;
  finalCost: number;
  grossTimeHours: number;
  itemizedBreakdown: BreakdownLine[];
  computedDueDate: string;
}

export interface StageAssignment {
  stageKey: string;
  assignedCreatorId: string | null;
  unitsCompleted: number;
  totalUnits: number;
  lastUpdatedAt: string | null;
}

export interface PaymentView {
  paymentId: string;
  type: PaymentType;
  amount: number;
  date: string;
  mode: string | null;
  note: string | null;
}

export interface ShipmentStopView {
  stopOrder: number;
  type: ShipmentStopType;
  originLocationCode: string;
  destinationLocationCode: string;
  laneId: string | null;
  estimatedCost: number;
  estimatedTimeHours: number;
  carrier: string | null;
  trackingNumber: string | null;
  triggerDate: string | null;
  shippedDate: string | null;
  deliveredConfirmed: boolean;
}

export interface StageProgressEntry {
  stageKey: string;
  unitsCompleted: number;
}

export interface SplitLine {
  creatorId: string;
  quantityAssigned: number;
  stageProgress: StageProgressEntry[];
}

export interface Variant {
  variantId: string;
  label: string;
  quantity: number;
  mandatoryItems: MandatoryItem[];
  tools: Tool[];
  addOns: LineItem[];
  packaging: Packaging | null;
  craftingTimeHours: number;
  assemblyTimeHours: number;
  perUnitCost: number;
  totalCost: number;
  perUnitTimeHours: number;
  totalTimeHours: number;
  splitAllocation: SplitLine[];
}

export interface BulkStageProgress {
  stageKey: string;
  unitsCompleted: number;
  totalUnits: number;
}

export interface BulkDetails {
  variants: Variant[];
  totalQuantity: number;
  totalFinalCost: number;
  totalTimeHours: number;
  coordinatingCreatorId: string | null;
  stageAssignments: StageAssignment[];
  stageProgress: BulkStageProgress[];
  logisticsBufferDays: number;
  computedDueDate: string | null;
}

export interface OrderView {
  id: string;
  orderNumber: string;
  orderType: OrderType;
  customerId: string;
  createdByCreatorId: string;
  status: OrderStatus;
  itemName: string | null;
  orderReceivedDate: string | null;
  quotedDeliveryDate: string | null;
  actualDeliveryDate: string | null;
  pattern: Pattern | null;
  researchItems: ResearchItem[];
  researchTimeHours: number;
  recipeSteps: string[];
  assemblyPackagingInstructions: string | null;
  notes: string | null;
  mandatoryItems: MandatoryItem[];
  tools: Tool[];
  addOns: LineItem[];
  packaging: Packaging | null;
  craftingTimeHours: number;
  assemblyTimeHours: number;
  costEstimate: CostEstimate | null;
  stageAssignments: StageAssignment[];
  completionPercentage: number;
  payments: PaymentView[];
  paymentStatus: PaymentStatus;
  netPaid: number;
  balanceAmount: number;
  shipmentPlan: ShipmentStopView[];
  bulkDetails: BulkDetails | null;
  createdAt: string;
  updatedAt: string;
}

export interface ChangeLog {
  orderStatusChangeHistory: { status: OrderStatus; changedByCreatorId: string; changeTimestamp: string }[];
  assigneeChangeHistory: {
    stageKey: string;
    oldValue: string | null;
    newValue: string | null;
    changedByCreatorId: string;
    changeTimestamp: string;
  }[];
  deliveryDateChangeHistory: {
    oldValue: string | null;
    newValue: string | null;
    changedByCreatorId: string;
    changeTimestamp: string;
  }[];
  paymentStatusChangeHistory: {
    oldValue: PaymentStatus;
    newValue: PaymentStatus;
    changedByCreatorId: string;
    changeTimestamp: string;
  }[];
  quantityChangeHistory: {
    oldValue: number;
    newValue: number;
    variantId: string | null;
    changedByCreatorId: string;
    changeTimestamp: string;
  }[];
  splitReallocationHistory: {
    variantId: string;
    oldAllocation: SplitLine[];
    newAllocation: SplitLine[];
    changedByCreatorId: string;
    changeTimestamp: string;
  }[];
}
