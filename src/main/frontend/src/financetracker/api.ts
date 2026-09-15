import { api } from "../api/client";
import type { CreateLedgerEntryRequest, LedgerEntryView } from "./types";

const base = (groupId: string) => `/financetracker/groups/${groupId}`;

export const financeTrackerApi = {
  ledgerEntries: (groupId: string) => api.get<LedgerEntryView[]>(`${base(groupId)}/ledger`),
  logLedgerEntry: (groupId: string, body: CreateLedgerEntryRequest) =>
    api.post<LedgerEntryView>(`${base(groupId)}/ledger`, body),
};
