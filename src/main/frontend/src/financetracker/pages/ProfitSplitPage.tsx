import { useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { financeTrackerApi } from "../api";
import { useFinanceGroup } from "../FinanceGroupContext";
import { formatMoney } from "../../lib/format";
import type { ProfitDistributionRecipientInput, ProfitDistributionView } from "../types";

type RecipientDraft = { personId: string; unitsCompleted: string; overrideAmount: string };

const blankRecipient = (): RecipientDraft => ({ personId: "", unitsCompleted: "1", overrideAmount: "" });

/** Splits an order's profit among the people who worked on it — proportional to units
 *  completed by default, with a per-recipient manual override for any reason a coordinator
 *  deems fit (design decision). Like a business's cost-config change, it only takes effect
 *  once every current finance-group member unanimously accepts it; on approval, one INCOME
 *  ledger entry per recipient shows up on the Overview/Income pages automatically.
 *
 *  totalProfit is typed in by the proposer (read off the order's finalized numbers in Order
 *  Tracker) rather than fetched live — Finance Tracker and Order Tracker are separate
 *  applets linked only by a GroupLink, so this avoids a live cross-applet data read. */
export default function ProfitSplitPage() {
  const { currentFinanceGroup, currentGroupId } = useFinanceGroup();
  const { user } = useAuth();
  const [distributions, setDistributions] = useState<ProfitDistributionView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [generalSettlement, setGeneralSettlement] = useState(false);
  const [orderRefs, setOrderRefs] = useState<string[]>([""]);
  const [totalProfit, setTotalProfit] = useState("");
  const [recipients, setRecipients] = useState<RecipientDraft[]>([blankRecipient(), blankRecipient()]);
  const [submitting, setSubmitting] = useState(false);
  const [respondingId, setRespondingId] = useState<string | null>(null);

  const members = currentFinanceGroup?.members ?? [];
  const memberName = (id: string) => members.find((m) => m.id === id)?.name ?? "Unknown";

  const load = () => {
    if (!currentGroupId) return;
    setLoading(true);
    financeTrackerApi.profitDistributions(currentGroupId).then(setDistributions).finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  const pending = distributions.filter((d) => d.status === "PENDING");
  const resolved = distributions
    .filter((d) => d.status !== "PENDING")
    .sort((a, b) => (b.resolvedAt ?? b.createdAt).localeCompare(a.resolvedAt ?? a.createdAt));

  const resetForm = () => {
    setGeneralSettlement(false);
    setOrderRefs([""]);
    setTotalProfit("");
    setRecipients([blankRecipient(), blankRecipient()]);
    setShowForm(false);
  };

  const submitPropose = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentGroupId) return;
    setError(null);
    const profit = Number(totalProfit);
    if (!profit || profit < 0) {
      setError("Enter a total profit of zero or more");
      return;
    }
    const references = generalSettlement ? [] : orderRefs.map((r) => r.trim()).filter(Boolean);
    if (!generalSettlement && references.length === 0) {
      setError("Enter at least one order reference, or check \"General settlement\"");
      return;
    }
    const usable = recipients.filter((r) => r.personId);
    if (usable.length === 0) {
      setError("Add at least one recipient");
      return;
    }
    const body: ProfitDistributionRecipientInput[] = usable.map((r) => ({
      personId: r.personId,
      unitsCompleted: Number(r.unitsCompleted) || 0,
      overrideAmount: r.overrideAmount.trim() === "" ? null : Number(r.overrideAmount),
    }));

    setSubmitting(true);
    try {
      await financeTrackerApi.proposeProfitDistribution(currentGroupId, {
        orderReferences: references,
        totalProfit: profit,
        recipients: body,
      });
      resetForm();
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to propose the split");
    } finally {
      setSubmitting(false);
    }
  };

  const updateOrderRef = (index: number, value: string) =>
    setOrderRefs(orderRefs.map((r, i) => (i === index ? value : r)));

  const orderLabel = (refs: string[]) => (refs.length > 0 ? refs.join(", ") : "General settlement");

  const respond = async (requestId: string, approve: boolean) => {
    if (!currentGroupId) return;
    setError(null);
    setRespondingId(requestId);
    try {
      await (approve
        ? financeTrackerApi.approveProfitDistribution(currentGroupId, requestId)
        : financeTrackerApi.rejectProfitDistribution(currentGroupId, requestId));
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to respond");
    } finally {
      setRespondingId(null);
    }
  };

  const updateRecipient = (index: number, patch: Partial<RecipientDraft>) =>
    setRecipients(recipients.map((r, i) => (i === index ? { ...r, ...patch } : r)));

  return (
    <div>
      <h1 className="page-title">Profit split</h1>
      <p className="page-sub">{currentFinanceGroup?.name}</p>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <h2>Pending proposals</h2>
        {loading ? (
          <p className="muted">Loading…</p>
        ) : pending.length === 0 ? (
          <p className="empty">Nothing pending.</p>
        ) : (
          pending.map((d) => {
            const haveApproved = d.approvedByUserIds.includes(user?.id ?? "");
            return (
              <div className="card" key={d.requestId} style={{ background: "var(--bg-elev-2)", marginBottom: 10 }}>
                <div className="row"><span className="k">Order</span><span className="v">{orderLabel(d.orderReferences)}</span></div>
                <div className="row"><span className="k">Total profit</span><span className="v">{formatMoney(d.totalProfit)}</span></div>
                {d.recipients.map((r) => (
                  <div className="row" key={r.personId}>
                    <span className="k">{memberName(r.personId)}</span>
                    <span className="v mono">{formatMoney(r.amount)}</span>
                  </div>
                ))}
                <p className="muted" style={{ fontSize: 13 }}>
                  Approved by {d.approvedByUserIds.length}/{d.groupMemberIds.length} member(s) so far.
                </p>
                {haveApproved ? (
                  <p className="hint">You've approved this — waiting on everyone else.</p>
                ) : (
                  <div className="toolbar">
                    <button
                      className="primary"
                      aria-label={`Approve profit split for ${orderLabel(d.orderReferences)}`}
                      disabled={respondingId === d.requestId}
                      onClick={() => respond(d.requestId, true)}
                    >
                      Approve
                    </button>
                    <button
                      aria-label={`Reject profit split for ${orderLabel(d.orderReferences)}`}
                      disabled={respondingId === d.requestId}
                      onClick={() => respond(d.requestId, false)}
                    >
                      Reject
                    </button>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <div className="toolbar">
          <h2 style={{ margin: 0 }}>Propose a split</h2>
          <span className="spacer" />
          {!showForm && (
            <button type="button" onClick={() => setShowForm(true)}>
              + New proposal
            </button>
          )}
        </div>

        {showForm && (
          <form onSubmit={submitPropose} style={{ marginTop: 12 }}>
            <div className="form-row">
              <label style={{ display: "flex", gap: 6, alignItems: "center" }}>
                <input
                  type="checkbox"
                  checked={generalSettlement}
                  onChange={(e) => setGeneralSettlement(e.target.checked)}
                />
                General settlement (not tied to a specific order — e.g. you don't remember which one)
              </label>
            </div>

            {!generalSettlement && (
              <div className="form-row">
                <label>Order reference(s)</label>
                <p className="hint" style={{ marginTop: 0 }}>
                  One split can cover several orders — add another row to settle them together.
                </p>
                {orderRefs.map((r, i) => (
                  <div key={i} style={{ display: "flex", gap: 8, marginBottom: 6 }}>
                    <input
                      aria-label={`Order reference ${i + 1}`}
                      placeholder="e.g. Order #94561842000001"
                      value={r}
                      onChange={(e) => updateOrderRef(i, e.target.value)}
                      style={{ flex: 1 }}
                    />
                    <button
                      type="button"
                      aria-label={`Remove order reference ${i + 1}`}
                      onClick={() => setOrderRefs(orderRefs.filter((_, idx) => idx !== i))}
                      disabled={orderRefs.length <= 1}
                    >
                      Remove
                    </button>
                  </div>
                ))}
                <button type="button" onClick={() => setOrderRefs([...orderRefs, ""])}>
                  + Add order reference
                </button>
              </div>
            )}

            <div className="form-grid">
              <div className="form-row">
                <label htmlFor="ps-total-profit">Total profit *</label>
                <input
                  id="ps-total-profit"
                  type="number"
                  min={0}
                  step={0.01}
                  value={totalProfit}
                  onChange={(e) => setTotalProfit(e.target.value)}
                  required
                />
              </div>
            </div>

            <h3 style={{ marginBottom: 6 }}>Recipients</h3>
            <p className="hint" style={{ marginTop: 0 }}>
              Leave "Override amount" blank to split proportionally by units completed; set it to fix that
              recipient's share regardless of units.
            </p>
            {recipients.map((r, i) => (
              <div className="form-grid" key={i} style={{ alignItems: "end" }}>
                <div className="form-row">
                  <label htmlFor={`ps-recipient-${i}`}>Person</label>
                  <select
                    id={`ps-recipient-${i}`}
                    value={r.personId}
                    onChange={(e) => updateRecipient(i, { personId: e.target.value })}
                  >
                    <option value="">Select…</option>
                    {members.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="form-row">
                  <label htmlFor={`ps-units-${i}`}>Units completed</label>
                  <input
                    id={`ps-units-${i}`}
                    type="number"
                    min={0}
                    value={r.unitsCompleted}
                    onChange={(e) => updateRecipient(i, { unitsCompleted: e.target.value })}
                  />
                </div>
                <div className="form-row">
                  <label htmlFor={`ps-override-${i}`}>Override amount</label>
                  <input
                    id={`ps-override-${i}`}
                    type="number"
                    min={0}
                    step={0.01}
                    placeholder="optional"
                    value={r.overrideAmount}
                    onChange={(e) => updateRecipient(i, { overrideAmount: e.target.value })}
                  />
                </div>
                <button
                  type="button"
                  aria-label={`Remove recipient ${i + 1}${r.personId ? `: ${memberName(r.personId)}` : ""}`}
                  onClick={() => setRecipients(recipients.filter((_, idx) => idx !== i))}
                  disabled={recipients.length <= 1}
                >
                  Remove
                </button>
              </div>
            ))}
            <button type="button" onClick={() => setRecipients([...recipients, blankRecipient()])} style={{ marginBottom: 12 }}>
              + Add recipient
            </button>

            <div className="toolbar">
              <button className="primary" type="submit" disabled={submitting}>
                {submitting ? "Proposing…" : "Propose split"}
              </button>
              <button type="button" onClick={resetForm}>
                Cancel
              </button>
            </div>
          </form>
        )}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>History</h2>
        {resolved.length === 0 ? (
          <p className="empty">No resolved proposals yet.</p>
        ) : (
          <div className="table-wrap">
            <table className="ot-table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Total profit</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {resolved.map((d) => (
                  <tr key={d.requestId}>
                    <td className="cell-title">{orderLabel(d.orderReferences)}</td>
                    <td className="cell-order mono">{formatMoney(d.totalProfit)}</td>
                    <td className="cell-type">{d.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
