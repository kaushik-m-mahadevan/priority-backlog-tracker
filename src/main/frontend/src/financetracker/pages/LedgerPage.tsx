import { useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { AsyncSection } from "../../components/AsyncSection";
import { BillFileInput, BillPreview, LEDGER_ENTRY_OWNER_TYPE } from "../../components/BillAttachmentField";
import BillSideBySide from "../../components/BillSideBySide";
import { imagesApi } from "../../components/imagesApi";
import { formatMoney } from "../../lib/format";
import { financeTrackerApi } from "../api";
import { useFinanceGroup } from "../FinanceGroupContext";
import type { ExternalPartySuggestion, LedgerEntryView, MemberBalanceView, PartyInput, PartyType } from "../types";

type PartyDraft = { type: PartyType; userId: string; displayName: string };

const blankParty = (defaultUserId = ""): PartyDraft => ({ type: "MEMBER", userId: defaultUserId, displayName: "" });

function toPartyInput(draft: PartyDraft): PartyInput | null {
  if (draft.type === "MEMBER") {
    return draft.userId ? { type: "MEMBER", userId: draft.userId, displayName: null } : null;
  }
  if (draft.type === "BUSINESS") {
    return { type: "BUSINESS", userId: null, displayName: null };
  }
  return draft.displayName.trim() ? { type: draft.type, userId: null, displayName: draft.displayName.trim() } : null;
}

/** ad-1: one unified double-entry ledger, replacing the earlier separate Expenses/Income
 *  pages — every row is exactly one Debit party and one Credit party, never a split (see
 *  LedgerEntry's own doc comment). "Business Account" is only offered as a party once this
 *  group's Business Settings has it configured (ManageFinanceGroupPage). */
export default function LedgerPage() {
  const { currentFinanceGroup, currentGroupId } = useFinanceGroup();
  const { user } = useAuth();
  const [entries, setEntries] = useState<LedgerEntryView[]>([]);
  const [balances, setBalances] = useState<MemberBalanceView[]>([]);
  const [businessAccountEnabled, setBusinessAccountEnabled] = useState(false);
  const [suggestions, setSuggestions] = useState<ExternalPartySuggestion[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState("");
  const [debit, setDebit] = useState<PartyDraft>(blankParty());
  const [credit, setCredit] = useState<PartyDraft>(blankParty(user?.id ?? ""));
  const [submitting, setSubmitting] = useState(false);
  const [billFile, setBillFile] = useState<File | null>(null);

  const members = currentFinanceGroup?.members ?? [];
  const memberName = (id: string | null) => members.find((m) => m.id === id)?.name ?? "Unknown";

  const load = () => {
    if (!currentGroupId) return;
    setLoading(true);
    Promise.all([
      financeTrackerApi.ledgerEntries(currentGroupId),
      financeTrackerApi.balances(currentGroupId),
      financeTrackerApi.businessConfig(currentGroupId),
      financeTrackerApi.externalSuggestions(currentGroupId),
    ])
      .then(([e, b, cfg, sug]) => {
        setEntries(e);
        setBalances(b);
        setBusinessAccountEnabled(cfg.businessAccountConfigured);
        setSuggestions(sug);
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  const resetForm = () => {
    setDescription("");
    setAmount("");
    setDate("");
    setDebit(blankParty());
    setCredit(blankParty(user?.id ?? ""));
    setBillFile(null);
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const amt = Number(amount);
    const debitInput = toPartyInput(debit);
    const creditInput = toPartyInput(credit);
    if (!description.trim() || !amt || amt <= 0 || !debitInput || !creditInput || !currentGroupId) {
      setError("Fill in a description, a positive amount, and both parties");
      return;
    }

    setSubmitting(true);
    try {
      const created = await financeTrackerApi.logLedgerEntry(currentGroupId, {
        date: date ? new Date(date).toISOString() : null,
        description: description.trim(),
        amount: amt,
        debit: debitInput,
        credit: creditInput,
      });
      const uploadWarning = await imagesApi.uploadIfAny(currentGroupId, LEDGER_ENTRY_OWNER_TYPE, created.id, billFile, "Ledger entry logged");
      if (uploadWarning) setError(uploadWarning);
      resetForm();
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to log this entry");
    } finally {
      setSubmitting(false);
    }
  };

  const removeEntry = async (entry: LedgerEntryView) => {
    if (!currentGroupId) return;
    if (!window.confirm(`Remove "${entry.description}"?`)) return;
    await financeTrackerApi.deleteLedgerEntry(currentGroupId, entry.id);
    load();
  };

  const partyLabel = (p: { type: PartyType; userId: string | null; displayName: string | null }) => {
    if (p.type === "MEMBER") return p.userId === user?.id ? "You" : memberName(p.userId);
    if (p.type === "BUSINESS") return "Business Account";
    return p.displayName ?? "—";
  };

  const partyPicker = (draft: PartyDraft, setDraft: (d: PartyDraft) => void, idPrefix: string) => (
    <div className="form-row">
      <label htmlFor={`${idPrefix}-type`}>{idPrefix === "debit" ? "Debit (who paid)" : "Credit (who received)"}</label>
      <select
        id={`${idPrefix}-type`}
        value={draft.type}
        onChange={(e) => setDraft({ ...draft, type: e.target.value as PartyType })}
      >
        <option value="MEMBER">A member</option>
        {businessAccountEnabled && <option value="BUSINESS">Business Account</option>}
        <option value="CUSTOMER">Customer (name)</option>
        <option value="EXTERNAL">Someone else (name)</option>
      </select>
      {draft.type === "MEMBER" && (
        <select
          aria-label={`${idPrefix} member`}
          value={draft.userId}
          onChange={(e) => setDraft({ ...draft, userId: e.target.value })}
          style={{ marginTop: 6 }}
        >
          <option value="">Select…</option>
          {members.map((m) => (
            <option key={m.id} value={m.id}>
              {m.id === user?.id ? "You" : m.name}
            </option>
          ))}
        </select>
      )}
      {(draft.type === "CUSTOMER" || draft.type === "EXTERNAL") && (
        <>
          <input
            aria-label={`${idPrefix} name`}
            placeholder="Name"
            value={draft.displayName}
            onChange={(e) => setDraft({ ...draft, displayName: e.target.value })}
            style={{ marginTop: 6 }}
            list={draft.type === "EXTERNAL" ? `${idPrefix}-external-suggestions` : undefined}
          />
          {draft.type === "EXTERNAL" && (
            <datalist id={`${idPrefix}-external-suggestions`}>
              {suggestions.map((s) => (
                <option key={s.displayName} value={s.displayName} />
              ))}
            </datalist>
          )}
        </>
      )}
    </div>
  );

  return (
    <div>
      <h1 className="page-title">Ledger</h1>
      <p className="page-sub">{currentFinanceGroup?.name}</p>

      {balances.length > 0 && (
        <div className="card" style={{ marginBottom: 16 }}>
          <h2>Balances</h2>
          <div className="kv">
            {balances.map((b) => (
              <span key={b.userId} className="chip">
                {b.userId === user?.id ? "You" : memberName(b.userId)}: {formatMoney(Math.abs(b.net))}
                {b.net >= 0 ? " owed to them" : " they owe"}
              </span>
            ))}
          </div>
        </div>
      )}

      <form className="card" onSubmit={submit} style={{ marginBottom: 16 }}>
        {error && <div className="error">{error}</div>}
        <BillSideBySide preview={<BillPreview editingId={null} groupId={currentGroupId!} billFile={billFile} />}>
          <div className="form-row">
            <label htmlFor="ledger-description">Description</label>
            <input id="ledger-description" value={description} onChange={(e) => setDescription(e.target.value)} required />
          </div>
          <div className="form-grid">
            <div className="form-row">
              <label htmlFor="ledger-amount">Amount</label>
              <input id="ledger-amount" type="number" min={0.01} step={0.01} value={amount}
                onChange={(e) => setAmount(e.target.value)} required />
            </div>
            <div className="form-row">
              <label htmlFor="ledger-date">Date (optional)</label>
              <input id="ledger-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </div>
          </div>

          <div className="form-grid">
            {partyPicker(debit, setDebit, "debit")}
            {partyPicker(credit, setCredit, "credit")}
          </div>

          <BillFileInput id="ledger-bill" editingId={null} onChange={setBillFile} />

          <div className="toolbar">
            <button className="primary" type="submit" disabled={submitting}>
              {submitting ? "Saving…" : "Log entry"}
            </button>
          </div>
        </BillSideBySide>
      </form>

      <AsyncSection loading={loading} isEmpty={entries.length === 0} empty={<p className="empty">No ledger entries yet.</p>}>
        <div className="table-wrap">
          <table className="ot-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Amount</th>
                <th>Debit</th>
                <th>Credit</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e) => (
                <tr key={e.id}>
                  <td className="cell-subtitle">{new Date(e.date).toLocaleDateString()}</td>
                  <td className="cell-title">
                    {e.description}
                    {e.sourceRef && <span className="badge" style={{ marginLeft: 6, fontSize: 10 }}>auto-synced</span>}
                  </td>
                  <td className="cell-order mono">{formatMoney(e.amount)}</td>
                  <td className="cell-subtitle">{partyLabel(e.debit)}</td>
                  <td className="cell-subtitle">{partyLabel(e.credit)}</td>
                  <td>
                    <button type="button" className="linkbtn" aria-label={`Remove: ${e.description}`} onClick={() => removeEntry(e)}>
                      remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </AsyncSection>
    </div>
  );
}
