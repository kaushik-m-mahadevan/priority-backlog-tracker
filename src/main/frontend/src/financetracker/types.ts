export type PartyType = "MEMBER" | "BUSINESS" | "CUSTOMER" | "EXTERNAL";

export interface PartyView {
  type: PartyType;
  userId: string | null;
  displayName: string | null;
}

export interface PartyInput {
  type: PartyType;
  userId: string | null;
  displayName: string | null;
}

export interface LedgerEntryView {
  id: string;
  date: string;
  description: string;
  amount: number;
  debit: PartyView;
  credit: PartyView;
  /** Set only for a row auto-created from an Order Tracker payment. */
  sourceRef: string | null;
  createdByUserId: string;
  createdAt: string;
}

export interface CreateLedgerEntryRequest {
  date: string | null;
  description: string;
  amount: number;
  debit: PartyInput;
  credit: PartyInput;
}

export interface MemberBalanceView {
  userId: string;
  net: number;
}

export interface ExternalPartySuggestion {
  displayName: string;
  useCount: number;
}

export interface FinanceBusinessConfig {
  groupId: string;
  businessAccountConfigured: boolean;
}

export type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED" | "INVALIDATED";

export interface ProfitDistributionRecipientInput {
  personId: string;
  unitsCompleted: number;
  overrideAmount: number | null;
}

export interface ProposeProfitDistributionRequest {
  /** Empty means a "general settlement" not tied to any specific order. */
  orderReferences: string[];
  totalProfit: number;
  recipients: ProfitDistributionRecipientInput[];
}

export interface ProfitDistributionRecipientAmount {
  personId: string;
  amount: number;
}

/** mb-18: one linked order's real split allocation, keyed by platform user id (same id
 *  space as ProfitDistributionRecipientInput.personId) so it can be dropped straight into
 *  the proposal form without translation. */
export interface OrderSplitView {
  reference: string;
  recipients: { userId: string; unitsCompleted: number }[];
}

export interface ProfitDistributionView {
  requestId: string;
  status: ApprovalStatus;
  orderReferences: string[];
  totalProfit: number;
  recipients: ProfitDistributionRecipientAmount[];
  proposedByUserId: string;
  approvedByUserIds: string[];
  groupMemberIds: string[];
  createdAt: string;
  resolvedAt: string | null;
}
