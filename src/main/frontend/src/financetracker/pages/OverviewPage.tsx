import { useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { financeTrackerApi } from "../api";
import { useFinanceGroup } from "../FinanceGroupContext";
import type { BalanceView } from "../types";

type SettleForm = { kind: "person" | "business"; fromPersonId: string; amount: string };

/** Each person's standing in the ledger, computed from every expense/income entry — see
 *  the backend's BalanceView doc comment for the exact rules. Two separate numbers on
 *  purpose: what other people owe you (or you owe them) is a different relationship from
 *  what the business itself owes you (a business-attributed expense you fronted, or an
 *  investment credited to you) — full transparency, so every member sees everyone's row,
 *  not just their own. "Settle up" only appears on your own row, since only the person
 *  actually owed money may record that they received it. */
export default function OverviewPage() {
  const { currentFinanceGroup, currentGroupId } = useFinanceGroup();
  const { user } = useAuth();
  const [balances, setBalances] = useState<BalanceView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [settleForm, setSettleForm] = useState<SettleForm | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const members = currentFinanceGroup?.members ?? [];
  const memberName = (id: string) => members.find((m) => m.id === id)?.name ?? "Unknown";

  const load = () => {
    if (!currentGroupId) return;
    setLoading(true);
    financeTrackerApi.balances(currentGroupId).then(setBalances).finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  const fmt = (n: number) => (n < 0 ? `-₹${Math.abs(n).toFixed(2)}` : `₹${n.toFixed(2)}`);

  const submitSettlement = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!settleForm || !currentGroupId) return;
    const amt = Number(settleForm.amount);
    if (!amt || amt <= 0) return;
    if (settleForm.kind === "person" && !settleForm.fromPersonId) {
      setError("Pick who paid you");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await financeTrackerApi.settleUp(currentGroupId, {
        fromPartyType: settleForm.kind === "business" ? "BUSINESS" : "PERSON",
        fromPersonId: settleForm.kind === "business" ? null : settleForm.fromPersonId,
        amount: amt,
      });
      setSettleForm(null);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record the settlement");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <h1 className="page-title">Overview</h1>
      <p className="page-sub">{currentFinanceGroup?.name}</p>

      <div className="card">
        <h2>Balances</h2>
        {error && <div className="error">{error}</div>}
        {loading ? (
          <p className="muted">Loading…</p>
        ) : balances.length === 0 ? (
          <p className="empty">Nothing logged yet — balances will show up here once an expense or income entry exists.</p>
        ) : (
          <div className="table-wrap">
            <table className="ot-table">
              <thead>
                <tr>
                  <th>Person</th>
                  <th>From other people</th>
                  <th>From the business</th>
                  <th>Settle up</th>
                </tr>
              </thead>
              <tbody>
                {balances.map((b) => {
                  const isMe = b.personId === user?.id;
                  return (
                    <tr key={b.personId}>
                      <td className="cell-title">{memberName(b.personId)}</td>
                      <td className="cell-order mono" style={b.netFromOthers < 0 ? { color: "var(--urgent)" } : undefined}>
                        {fmt(b.netFromOthers)}
                        <span className="muted"> {b.netFromOthers < 0 ? "owed" : b.netFromOthers > 0 ? "owed to them" : ""}</span>
                      </td>
                      <td className="cell-subtitle mono">{fmt(b.owedByBusiness)}</td>
                      <td className="cell-type">
                        {isMe && b.netFromOthers > 0 && (
                          <button
                            type="button"
                            onClick={() => setSettleForm({ kind: "person", fromPersonId: "", amount: b.netFromOthers.toFixed(2) })}
                          >
                            Someone paid me
                          </button>
                        )}
                        {isMe && b.owedByBusiness > 0 && (
                          <button
                            type="button"
                            style={{ marginLeft: isMe && b.netFromOthers > 0 ? 6 : 0 }}
                            onClick={() => setSettleForm({ kind: "business", fromPersonId: "", amount: b.owedByBusiness.toFixed(2) })}
                          >
                            Business paid me
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <p className="hint" style={{ marginTop: 10 }}>
          "From other people" nets out personal splits — positive means others owe this person, negative means
          they owe others. "From the business" is separate: business-attributed expenses this person fronted
          (including reimbursements) plus any income credited directly to them (e.g. an investment).
        </p>
      </div>

      {settleForm && (
        <form className="card" onSubmit={submitSettlement} style={{ marginTop: 16 }}>
          <h2>{settleForm.kind === "business" ? "Business paid me" : "Someone paid me"}</h2>
          {settleForm.kind === "person" && (
            <div className="form-row">
              <label htmlFor="settle-from">Who paid you</label>
              <select
                id="settle-from"
                value={settleForm.fromPersonId}
                onChange={(e) => setSettleForm({ ...settleForm, fromPersonId: e.target.value })}
                required
              >
                <option value="" disabled>
                  Select…
                </option>
                {members.filter((m) => m.id !== user?.id).map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name}
                  </option>
                ))}
              </select>
            </div>
          )}
          <div className="form-row" style={{ maxWidth: 200 }}>
            <label htmlFor="settle-amount">Amount</label>
            <input
              id="settle-amount"
              type="number"
              min={0.01}
              step={0.01}
              value={settleForm.amount}
              onChange={(e) => setSettleForm({ ...settleForm, amount: e.target.value })}
              required
            />
          </div>
          <button className="primary" type="submit" disabled={submitting}>
            {submitting ? "Recording…" : "Record"}
          </button>
          <button type="button" onClick={() => setSettleForm(null)} style={{ marginLeft: 8 }}>
            Cancel
          </button>
        </form>
      )}
    </div>
  );
}
