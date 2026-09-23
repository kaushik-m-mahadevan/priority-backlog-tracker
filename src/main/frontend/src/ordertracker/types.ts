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
export type DeliveryTier = "SAME_CITY" | "SAME_STATE" | "OTHER_STATE" | "INTERNATIONAL";

export interface CustomerAddress {
  addressId: string;
  label: string;
  address: string;
  isDefault: boolean;
}

export interface Customer {
  id: string;
  name: string;
  contactNumber: string | null;
  email: string | null;
  instagramHandle: string | null;
  acquisitionChannel: AcquisitionChannel;
  firstContactDate: string | null;
  /** ad-6: replaces the old single shippingAddress string — zero or more saved
   *  addresses, at most one of them marked default. */
  addresses: CustomerAddress[];
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
  autoSyncInventory: boolean;
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
  proposedHourlyWage: number;
  proposedByUserId: string;
  approvedByUserIds: string[];
  status: CostConfigChangeStatus;
  rejectedByUserId: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export type OrderFinalizationStatus = "NONE" | "PENDING" | "FINALIZED";

export interface OrderFinalizationView {
  status: OrderFinalizationStatus;
  finalCost: number;
  finalRevenue: number;
  finalProfit: number;
  finalizedAt: string | null;
  proposedByUserId: string | null;
  approvedByUserIds: string[];
  groupMemberIds: string[];
}

export interface BusinessConfig {
  groupId: string;
  overheadPercentage: number;
  profitMarginPercentage: number;
  currency: string;
  hourlyWage: number;
  hourlyWageConfirmed: boolean;
  deliveryBufferSameCityDays: number;
  deliveryBufferSameStateDays: number;
  deliveryBufferOtherStateDays: number;
  deliveryBufferInternationalDays: number;
  individualOrderTypeCode: string;
  bulkOrderTypeCode: string;
  workStages: WorkStageType[];
  setupComplete: boolean;
}

export interface PresetOption {
  id: string;
  label: string;
  estimatedCost: number;
  estimatedTimeHours: number;
}

export interface ComponentTemplate {
  id: string;
  label: string;
  pattern: Pattern | null;
  baseCraftingTimeHours: number;
  notes: string | null;
}

export interface Pattern {
  patternType: PatternType | null;
  templateName: string | null;
  customPatternNotes: string | null;
  attachmentUrls: string[];
  /** Lives here now, not as a separate top-level order field — a design's recipe is part
   *  of its pattern, matching the shared commons.pattern.domain.Pattern model. */
  recipeSteps: string[];
}

export interface ResearchItem {
  type: ResearchItemType;
  url: string | null;
  description: string | null;
}

export type MaterialKind = "YARN" | "NEEDLE";

export interface MandatoryItem {
  kind: MaterialKind;
  value: string;
  quantity: number;
  unitCost: number;
  notes: string | null;
  /** Optional — set only when {@code kind === "YARN"} and this business has a linked
   *  Material Inventory group. Opaque to Order Tracker's own backend; the frontend uses it
   *  to look up the current user's on-hand quantity for a shortfall check. */
  linkedYarnTypeId: string | null;
  /** Same idea as {@link linkedYarnTypeId}, but for {@code kind === "NEEDLE"}. */
  linkedNeedleTypeId: string | null;
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
  laborCost: number;
  grossCost: number;
  profitAmount: number;
  finalCost: number;
  grossTimeHours: number;
  workDays: number;
  deliveryBufferDays: number;
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
  receivedBy: string | null;
}

export type TimeStage = "RESEARCH" | "CRAFTING" | "ASSEMBLY";

export interface TimeLogEntryView {
  entryId: string;
  stage: TimeStage;
  hours: number;
  date: string;
  loggedByCreatorId: string;
  note: string | null;
}

export interface UsageLogEntryView {
  entryId: string;
  yarnTypeId: string;
  quantity: number;
  date: string;
  loggedByCreatorId: string;
  note: string | null;
  synced: boolean;
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

export interface ComponentInstance {
  componentId: string;
  templateId: string;
  label: string;
  templateCraftingTimeHours: number;
  quantity: number;
  mandatoryItems: MandatoryItem[];
  addOns: LineItem[];
  craftingTimeHours: number;
  perUnitCost: number;
  totalCost: number;
  perUnitTimeHours: number;
  totalTimeHours: number;
  timeLogEntries: TimeLogEntryView[];
}

export interface Variant {
  variantId: string;
  label: string;
  quantity: number;
  mandatoryItems: MandatoryItem[];
  addOns: LineItem[];
  components: ComponentInstance[];
  packaging: Packaging | null;
  craftingTimeHours: number;
  assemblyTimeHours: number;
  perUnitCost: number;
  totalCost: number;
  perUnitTimeHours: number;
  totalTimeHours: number;
  splitAllocation: SplitLine[];
  timeLogEntries: TimeLogEntryView[];
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
  deliveryBufferDays: number;
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
  deliveryTier: DeliveryTier;
  actualDeliveryDate: string | null;
  pattern: Pattern | null;
  researchItems: ResearchItem[];
  researchTimeHours: number;
  assemblyPackagingInstructions: string | null;
  notes: string | null;
  mandatoryItems: MandatoryItem[];
  addOns: LineItem[];
  components: ComponentInstance[];
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
  timeLogEntries: TimeLogEntryView[];
  usageLogEntries: UsageLogEntryView[];
  bulkDetails: BulkDetails | null;
  /** ad-3: only non-null once status === "CANCELLED". */
  cancellation: OrderCancellation | null;
  createdAt: string;
  updatedAt: string;
}

export interface OrderCancellation {
  reason: string;
  note: string | null;
  cancelledByUserId: string;
  cancelledAt: string;
  estimatedMaterialsLoss: number;
  estimatedLaborLoss: number;
}

export interface ChangeLog {
  orderStatusChangeHistory: {
    status: OrderStatus; changedByCreatorId: string; changeTimestamp: string; justification: string | null;
  }[];
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
