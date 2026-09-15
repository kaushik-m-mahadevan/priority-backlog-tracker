import { api } from "../api/client";
import type { BalanceView, CreateLedgerEntryRequest, CreateSettlementRequest, LedgerEntryView, SettlementView } from "./types";

const base = (groupId: string) => `/financetracker/groups/${groupId}`;

export const financeTrackerApi = {
  ledgerEntries: (groupId: string) => api.get<LedgerEntryView[]>(`${base(groupId)}/ledger`),
  logLedgerEntry: (groupId: string, body: CreateLedgerEntryRequest) =>
    api.post<LedgerEntryView>(`${base(groupId)}/ledger`, body),
  balances: (groupId: string) => api.get<BalanceView[]>(`${base(groupId)}/ledger/balances`),
  settlements: (groupId: string) => api.get<SettlementView[]>(`${base(groupId)}/ledger/settlements`),
  settleUp: (groupId: string, body: CreateSettlementRequest) =>
    api.post<SettlementView>(`${base(groupId)}/ledger/settlements`, body),
};
