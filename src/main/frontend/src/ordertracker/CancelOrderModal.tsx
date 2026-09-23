import { useState } from "react";
import { formatMoney } from "../lib/format";
import { orderTrackerApi } from "./api";
import { CANNED_CANCEL_REASONS } from "./orderStatusColumns";
import type { OrderView } from "./types";

/** ad-3's guided cancel flow — a separate, always-available action per order (not a board
 *  column). Reason is required; already-logged time and payments are never touched (a
 *  historical fact and a real ledger record, respectively — the backend leaves both
 *  exactly as they are). A refund, if any, is a deliberately separate step through the
 *  order's own ordinary "record a payment" flow (type = Refund) rather than part of this
 *  form — reusing what already exists instead of a parallel refund mechanism. The
 *  estimated-loss summary shown after cancelling is informational only, never a ledger
 *  entry (no cash moved for a pure loss). */
export default function CancelOrderModal({
  groupId,
  order,
  onCancelled,
  onClose,
}: {
  groupId: string;
  order: OrderView;
  onCancelled: (o: OrderView) => void;
  onClose: () => void;
}) {
  const [reason, setReason] = useState(CANNED_CANCEL_REASONS[0]);
  const [customReason, setCustomReason] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<OrderView | null>(null);

  const effectiveReason = reason === "Other" ? customReason.trim() : reason;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!effectiveReason) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await orderTrackerApi.cancelOrder(groupId, order.id, effectiveReason, note.trim() || undefined);
      setResult(updated);
      onCancelled(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cancel this order");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose} onKeyDown={(e) => { if (e.key === "Escape") onClose(); }}>
      <div className="modal" role="dialog" aria-modal="true" aria-label="Cancel order" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>Cancel order {order.orderNumber}</h3>
        </div>
        {error && <div className="error">{error}</div>}

        {result?.cancellation ? (
          <div>
            <p className="hint">This order is now cancelled.</p>
            {(result.cancellation.estimatedMaterialsLoss > 0 || result.cancellation.estimatedLaborLoss > 0) && (
              <div className="card" style={{ background: "var(--bg-elev-2)", marginTop: 8 }}>
                <h2 style={{ fontSize: 14 }}>Estimated loss (informational only)</h2>
                <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
                  Not a ledger entry — no cash moved for a pure loss. Already-logged material usage and time stay
                  exactly as logged.
                </p>
                <div className="row"><span className="k">Materials at risk</span><span className="v">{formatMoney(result.cancellation.estimatedMaterialsLoss)}</span></div>
                <div className="row"><span className="k">Unrecovered labor</span><span className="v">{formatMoney(result.cancellation.estimatedLaborLoss)}</span></div>
              </div>
            )}
            <p className="hint" style={{ marginTop: 10 }}>
              If a refund is owed, record it from the Logistics section below (Payment type: Refund).
            </p>
            <div className="modal-actions">
              <button className="primary" type="button" onClick={onClose}>
                Done
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={submit}>
            <div className="form-row">
              <label htmlFor="cancel-reason">Reason *</label>
              <select id="cancel-reason" value={reason} onChange={(e) => setReason(e.target.value)} required>
                {CANNED_CANCEL_REASONS.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
                <option value="Other">Other</option>
              </select>
            </div>
            {reason === "Other" && (
              <div className="form-row">
                <label htmlFor="cancel-custom-reason" className="sr-only">
                  Describe the reason
                </label>
                <input
                  id="cancel-custom-reason"
                  placeholder="Describe the reason"
                  value={customReason}
                  onChange={(e) => setCustomReason(e.target.value)}
                  required
                />
              </div>
            )}
            <div className="form-row">
              <label htmlFor="cancel-note">Additional note (optional)</label>
              <textarea id="cancel-note" value={note} onChange={(e) => setNote(e.target.value)} />
            </div>
            <p className="hint">
              Logged time and any payments already recorded stay exactly as they are — cancelling only stops the
              order moving forward.
            </p>
            <div className="modal-actions">
              <button type="button" className="ghost" onClick={onClose} disabled={busy}>
                Never mind
              </button>
              <button type="submit" className="primary" disabled={busy || !effectiveReason}>
                {busy ? "Cancelling…" : "Cancel this order"}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
