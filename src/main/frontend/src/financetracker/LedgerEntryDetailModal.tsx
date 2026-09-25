import { useEffect, useRef, useState } from "react";
import { BILL_ACCEPT, LEDGER_ENTRY_OWNER_TYPE } from "../components/BillAttachmentField";
import ImageGallery from "../components/ImageGallery";
import { formatDateTime } from "../lib/format";
import { financeTrackerApi } from "./api";
import { PartyPicker, blankPartyDraft, partyDraftFromView, toPartyInput, type PartyDraft } from "./PartyPicker";
import type { ExternalPartySuggestion, LedgerEntryView, PartyType } from "./types";

/** Every field a ledger row carries, viewable and (for a manually-entered row) editable in
 *  place — previously the only way to see a row's bill/notes/order reference was the create
 *  form itself, and there was no way at all to correct a typo short of delete-and-recreate.
 *  A synced row (sourceRef set, mirrored from an Order Tracker payment) stays locked to its
 *  source on every field except notes, so editing here can never drift it from the real
 *  payment it represents — same boundary its delete already respected. */
export default function LedgerEntryDetailModal({
  entry,
  groupId,
  members,
  currentUserId,
  businessAccountEnabled,
  suggestions,
  memberName,
  onClose,
  onSaved,
}: {
  entry: LedgerEntryView;
  groupId: string;
  members: { id: string; name: string }[];
  currentUserId: string | undefined;
  businessAccountEnabled: boolean;
  suggestions: ExternalPartySuggestion[];
  memberName: (id: string | null) => string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const editableFields = entry.sourceRef === null;

  const [description, setDescription] = useState(entry.description);
  const [amount, setAmount] = useState(String(entry.amount));
  const [date, setDate] = useState(entry.date.slice(0, 10));
  const [orderReference, setOrderReference] = useState(entry.orderReference ?? "");
  const [notes, setNotes] = useState(entry.notes ?? "");
  const [debit, setDebit] = useState<PartyDraft>(partyDraftFromView(entry.debit));
  const [credit, setCredit] = useState<PartyDraft>(partyDraftFromView(entry.credit));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dialogRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    return () => opener?.focus?.();
  }, []);

  const partyLabel = (type: PartyType, userId: string | null, displayName: string | null) => {
    if (type === "MEMBER") return userId === currentUserId ? "You" : memberName(userId);
    if (type === "BUSINESS") return "Business Account";
    return displayName ?? "—";
  };

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const debitInput = toPartyInput(debit);
    const creditInput = toPartyInput(credit);
    const amt = Number(amount);
    if (editableFields) {
      if (!description.trim() || !amt || amt <= 0 || !debitInput || !creditInput) {
        setError("Fill in a description, a positive amount, and both parties");
        return;
      }
      const involvesCustomer = debitInput.type === "CUSTOMER" || creditInput.type === "CUSTOMER";
      if (involvesCustomer && !orderReference.trim()) {
        setError("Order reference is required for a payment involving a customer");
        return;
      }
    }
    setBusy(true);
    try {
      await financeTrackerApi.updateLedgerEntry(groupId, entry.id, {
        date: date ? new Date(date + "T00:00:00Z").toISOString() : null,
        description: description.trim(),
        amount: amt,
        debit: debitInput ?? { type: entry.debit.type, userId: entry.debit.userId, displayName: entry.debit.displayName },
        credit: creditInput ?? { type: entry.credit.type, userId: entry.credit.userId, displayName: entry.credit.displayName },
        orderReference: orderReference.trim() || null,
        notes: notes.trim() || null,
      });
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save changes");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal"
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="ledger-entry-modal-title"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={(e) => e.key === "Escape" && onClose()}
      >
        <div className="modal-head">
          <h3 id="ledger-entry-modal-title">Ledger entry</h3>
          {entry.sourceRef && <span className="badge">auto-synced</span>}
        </div>
        {error && <div className="error">{error}</div>}
        {!editableFields && (
          <p className="hint" style={{ marginTop: 0 }}>
            This row was synced from an Order Tracker payment — only notes can be edited here. Use the order's own
            "remove payment" to change the amount or remove it.
          </p>
        )}

        <form onSubmit={submit}>
          <div className="form-row">
            <label htmlFor="led-detail-description">Description</label>
            <input
              id="led-detail-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={!editableFields}
              required
            />
          </div>

          <div className="form-grid">
            <div className="form-row">
              <label htmlFor="led-detail-amount">Amount</label>
              <input
                id="led-detail-amount"
                type="number"
                min={0.01}
                step={0.01}
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                disabled={!editableFields}
                required
              />
            </div>
            <div className="form-row">
              <label htmlFor="led-detail-date">Date</label>
              <input
                id="led-detail-date"
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                disabled={!editableFields}
              />
            </div>
          </div>

          {editableFields ? (
            <div className="form-grid">
              <PartyPicker
                draft={debit}
                setDraft={setDebit}
                idPrefix="led-detail-debit"
                label="Debit (who paid)"
                members={members}
                currentUserId={currentUserId}
                businessAccountEnabled={businessAccountEnabled}
                suggestions={suggestions}
              />
              <PartyPicker
                draft={credit}
                setDraft={setCredit}
                idPrefix="led-detail-credit"
                label="Credit (who received)"
                members={members}
                currentUserId={currentUserId}
                businessAccountEnabled={businessAccountEnabled}
                suggestions={suggestions}
              />
            </div>
          ) : (
            <div className="detail-facts">
              <span>Debit</span>
              <span>{partyLabel(entry.debit.type, entry.debit.userId, entry.debit.displayName)}</span>
              <span>Credit</span>
              <span>{partyLabel(entry.credit.type, entry.credit.userId, entry.credit.displayName)}</span>
            </div>
          )}

          <div className="form-row">
            <label htmlFor="led-detail-order-reference">
              Order reference{editableFields && (debit.type === "CUSTOMER" || credit.type === "CUSTOMER") ? "" : " (optional)"}
            </label>
            <input
              id="led-detail-order-reference"
              placeholder="e.g. Order #94561842000001"
              value={orderReference}
              onChange={(e) => setOrderReference(e.target.value)}
              disabled={!editableFields}
              required={editableFields && (debit.type === "CUSTOMER" || credit.type === "CUSTOMER")}
            />
          </div>

          <div className="form-row">
            <label htmlFor="led-detail-notes">Notes</label>
            <textarea
              id="led-detail-notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="How it was paid, a tracking detail, anything worth remembering later"
            />
          </div>

          <div className="form-row">
            <label>Bill / invoice</label>
            <ImageGallery groupId={groupId} ownerType={LEDGER_ENTRY_OWNER_TYPE} ownerId={entry.id} accept={BILL_ACCEPT} />
          </div>

          <div className="detail-facts">
            <span>Logged</span>
            <span>{formatDateTime(entry.createdAt)} · {memberName(entry.createdByUserId)}</span>
          </div>

          <div className="modal-actions">
            <button type="button" className="ghost" onClick={onClose} disabled={busy}>
              Close
            </button>
            <button type="submit" className="primary" disabled={busy}>
              {busy ? "Saving…" : "Save changes"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
