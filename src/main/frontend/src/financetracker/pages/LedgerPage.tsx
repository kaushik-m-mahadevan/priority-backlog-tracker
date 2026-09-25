import { useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { AsyncSection } from "../../components/AsyncSection";
import { BillFileInput, BillPreview } from "../../components/BillAttachmentField";
import BillSideBySide from "../../components/BillSideBySide";
import { imagesApi } from "../../components/imagesApi";
import { LEDGER_ENTRY_OWNER_TYPE } from "../../components/BillAttachmentField";
import { formatMoney } from "../../lib/format";
import { financeTrackerApi } from "../api";
import { useFinanceGroup } from "../FinanceGroupContext";
import LedgerEntryDetailModal from "../LedgerEntryDetailModal";
import { PartyPicker, blankPartyDraft, toPartyInput, type PartyDraft } from "../PartyPicker";
import type { ExternalPartySuggestion, LedgerEntryView, MemberBalanceView, PartyType } from "../types";

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
  const [orderReference, setOrderReference] = useState("");
  const [notes, setNotes] = useState("");
  const [debit, setDebit] = useState<PartyDraft>(blankPartyDraft());
  const [credit, setCredit] = useState<PartyDraft>(blankPartyDraft(user?.id ?? ""));
  const [submitting, setSubmitting] = useState(false);
  const [billFile, setBillFile] = useState<File | null>(null);
  const [viewing, setViewing] = useState<LedgerEntryView | null>(null);

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
    setDebit(blankPartyDraft());
    setCredit(blankPartyDraft(user?.id ?? ""));
    setOrderReference("");
    setNotes("");
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
    const involvesCustomer = debitInput.type === "CUSTOMER" || creditInput.type === "CUSTOMER";
    if (involvesCustomer && !orderReference.trim()) {
      setError("Order reference is required for a payment involving a customer");
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
        orderReference: orderReference.trim() || null,
        notes: notes.trim() || null,
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
            <PartyPicker
              draft={debit}
              setDraft={setDebit}
              idPrefix="ledger-debit"
              label="Debit (who paid)"
              members={members}
              currentUserId={user?.id}
              businessAccountEnabled={businessAccountEnabled}
              suggestions={suggestions}
            />
            <PartyPicker
              draft={credit}
              setDraft={setCredit}
              idPrefix="ledger-credit"
              label="Credit (who received)"
              members={members}
              currentUserId={user?.id}
              businessAccountEnabled={businessAccountEnabled}
              suggestions={suggestions}
            />
          </div>

          <div className="form-row">
            <label htmlFor="ledger-order-reference">
              Order reference{debit.type === "CUSTOMER" || credit.type === "CUSTOMER" ? "" : " (optional)"}
            </label>
            <input
              id="ledger-order-reference"
              placeholder="e.g. Order #94561842000001"
              value={orderReference}
              onChange={(e) => setOrderReference(e.target.value)}
              required={debit.type === "CUSTOMER" || credit.type === "CUSTOMER"}
            />
            <p className="hint" style={{ marginTop: 4 }}>
              {debit.type === "CUSTOMER" || credit.type === "CUSTOMER"
                ? "Required for a payment involving a customer — a plain note, not a live lookup."
                : "Ties this row to a specific order for your own reference — a plain note, not a live lookup."}
            </p>
          </div>

          <div className="form-row">
            <label htmlFor="ledger-notes">Notes (optional)</label>
            <textarea id="ledger-notes" value={notes} onChange={(e) => setNotes(e.target.value)}
              placeholder="How it was paid, a tracking detail, anything worth remembering later" />
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
                <tr
                  key={e.id}
                  className="row-clickable"
                  onClick={() => setViewing(e)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(ev) => (ev.key === "Enter" || ev.key === " ") && setViewing(e)}
                >
                  <td className="cell-subtitle">{new Date(e.date).toLocaleDateString()}</td>
                  <td className="cell-title">
                    {e.description}
                    {e.sourceRef && <span className="badge" style={{ marginLeft: 6, fontSize: 10 }}>auto-synced</span>}
                    {e.orderReference && (
                      <div className="muted" style={{ fontSize: 11, marginTop: 2 }}>{e.orderReference}</div>
                    )}
                  </td>
                  <td className="cell-order mono">{formatMoney(e.amount)}</td>
                  <td className="cell-subtitle">{partyLabel(e.debit)}</td>
                  <td className="cell-subtitle">{partyLabel(e.credit)}</td>
                  <td>
                    <button
                      type="button"
                      className="linkbtn"
                      aria-label={`Remove: ${e.description}`}
                      onClick={(ev) => {
                        ev.stopPropagation();
                        removeEntry(e);
                      }}
                    >
                      remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </AsyncSection>

      {viewing && currentGroupId && (
        <LedgerEntryDetailModal
          entry={viewing}
          groupId={currentGroupId}
          members={members}
          currentUserId={user?.id}
          businessAccountEnabled={businessAccountEnabled}
          suggestions={suggestions}
          memberName={memberName}
          onClose={() => setViewing(null)}
          onSaved={() => {
            setViewing(null);
            load();
          }}
        />
      )}
    </div>
  );
}
