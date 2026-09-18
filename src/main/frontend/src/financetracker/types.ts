export type LedgerEntryType = "EXPENSE" | "INCOME";
export type SplitPartyType = "PERSON" | "BUSINESS";

export interface ShareView {
  partyType: SplitPartyType;
  personId: string | null;
  ratio: number;
}

export interface LedgerEntryView {
  id: string;
  type: LedgerEntryType;
  description: string;
  amount: number;
  payerId: string;
  shares: ShareView[];
  createdByUserId: string;
  createdAt: string;
}

export interface ShareInput {
  partyType: SplitPartyType;
  personId: string | null;
  ratio: number;
}

export interface CreateLedgerEntryRequest {
  type: LedgerEntryType;
  description: string;
  amount: number;
  payerId: string;
  shares: ShareInput[];
}

export interface BalanceView {
  personId: string;
  netFromOthers: number;
  owedByBusiness: number;
}

export interface CreateSettlementRequest {
  fromPartyType: SplitPartyType;
  fromPersonId: string | null;
  amount: number;
}

export interface SettlementView {
  id: string;
  fromPartyType: SplitPartyType;
  fromPersonId: string | null;
  toPersonId: string;
  amount: number;
  createdAt: string;
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
