import { api } from "../api/client";
import type {
  CreateLedgerEntryRequest,
  ExternalPartySuggestion,
  FinanceBusinessConfig,
  LedgerEntryView,
  MemberBalanceView,
  OrderSplitView,
  ProfitDistributionView,
  ProposeProfitDistributionRequest,
} from "./types";

const base = (groupId: string) => `/financetracker/groups/${groupId}`;

export const financeTrackerApi = {
  ledgerEntries: (groupId: string) => api.get<LedgerEntryView[]>(`${base(groupId)}/ledger`),
  logLedgerEntry: (groupId: string, body: CreateLedgerEntryRequest) =>
    api.post<LedgerEntryView>(`${base(groupId)}/ledger`, body),
  deleteLedgerEntry: (groupId: string, entryId: string) => api.delete<void>(`${base(groupId)}/ledger/${entryId}`),
  balances: (groupId: string) => api.get<MemberBalanceView[]>(`${base(groupId)}/ledger/balances`),
  externalSuggestions: (groupId: string) => api.get<ExternalPartySuggestion[]>(`${base(groupId)}/ledger/external-suggestions`),

  businessConfig: (groupId: string) => api.get<FinanceBusinessConfig>(`${base(groupId)}/business-config`),
  setBusinessAccountConfigured: (groupId: string, businessAccountConfigured: boolean) =>
    api.put<FinanceBusinessConfig>(`${base(groupId)}/business-config`, { businessAccountConfigured }),

  profitDistributions: (groupId: string) => api.get<ProfitDistributionView[]>(`${base(groupId)}/profit-distributions`),
  lookupOrderSplit: (groupId: string, reference: string) =>
    api.get<OrderSplitView>(`${base(groupId)}/profit-distributions/order-split/${encodeURIComponent(reference)}`),
  proposeProfitDistribution: (groupId: string, body: ProposeProfitDistributionRequest) =>
    api.post<ProfitDistributionView>(`${base(groupId)}/profit-distributions`, body),
  approveProfitDistribution: (groupId: string, requestId: string) =>
    api.post<ProfitDistributionView>(`${base(groupId)}/profit-distributions/${requestId}/approve`),
  rejectProfitDistribution: (groupId: string, requestId: string) =>
    api.post<ProfitDistributionView>(`${base(groupId)}/profit-distributions/${requestId}/reject`),
};
