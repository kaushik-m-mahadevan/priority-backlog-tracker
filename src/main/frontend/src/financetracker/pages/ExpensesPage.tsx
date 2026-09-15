import { useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { financeTrackerApi } from "../api";
import { useFinanceGroup } from "../FinanceGroupContext";
import type { LedgerEntryView, ShareInput } from "../types";

type SplitMode = "business" | "split";

/** Logging a personal expenditure or a business-attributed one (which is how a
 *  reimbursement is modeled here — see LedgerEntry's own doc comment) is the same form:
 *  pick "Business" for the whole amount to be owed back to whoever paid, or "Split with
 *  members" for an equal split across everyone selected, including the payer if they're
 *  checked. A full custom-ratio editor is a separate future feature, not this one. */
export default function ExpensesPage() {
  const { currentFinanceGroup, currentGroupId } = useFinanceGroup();
  const { user } = useAuth();
  const [entries, setEntries] = useState<LedgerEntryView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [payerId, setPayerId] = useState(user?.id ?? "");
  const [splitMode, setSplitMode] = useState<SplitMode>("business");
  const [splitWith, setSplitWith] = useState<Set<string>>(new Set());
  const [submitting, setSubmitting] = useState(false);

  const members = currentFinanceGroup?.members ?? [];
  const memberName = (id: string) => members.find((m) => m.id === id)?.name ?? "Unknown";

  const load = () => {
    if (!currentGroupId) return;
    setLoading(true);
    financeTrackerApi
      .ledgerEntries(currentGroupId)
      .then(setEntries)
      .finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  useEffect(() => {
    if (payerId) setSplitWith((prev) => new Set(prev).add(payerId));
  }, [payerId]);

  const toggleSplitMember = (id: string) => {
    setSplitWith((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const amt = Number(amount);
    if (!description.trim() || !amt || amt <= 0 || !payerId || !currentGroupId) return;

    let shares: ShareInput[];
    if (splitMode === "business") {
      shares = [{ partyType: "BUSINESS", personId: null, ratio: 1 }];
    } else {
      const ids = [...splitWith];
      if (ids.length === 0) {
        setError("Pick at least one member to split with");
        return;
      }
      const ratio = 1 / ids.length;
      shares = ids.map((id) => ({ partyType: "PERSON", personId: id, ratio }));
    }

    setSubmitting(true);
    try {
      await financeTrackerApi.logLedgerEntry(currentGroupId, {
        type: "EXPENSE",
        description: description.trim(),
        amount: amt,
        payerId,
        shares,
      });
      setDescription("");
      setAmount("");
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to log expense");
    } finally {
      setSubmitting(false);
    }
  };

  const shareSummary = (entry: LedgerEntryView) => {
    if (entry.shares.length === 1 && entry.shares[0].partyType === "BUSINESS") {
      return "Business";
    }
    return entry.shares
      .map((s) => `${s.personId ? memberName(s.personId) : "Business"} ${(s.ratio * 100).toFixed(0)}%`)
      .join(", ");
  };

  return (
    <div>
      <h1 className="page-title">Expenses</h1>
      <p className="page-sub">{currentFinanceGroup?.name}</p>

      <form className="card" onSubmit={submit} style={{ marginBottom: 16 }}>
        {error && <div className="error">{error}</div>}
        <div className="form-row">
          <label htmlFor="exp-description">Description</label>
          <input id="exp-description" value={description} onChange={(e) => setDescription(e.target.value)} required />
        </div>
        <div className="form-grid">
          <div className="form-row">
            <label htmlFor="exp-amount">Amount</label>
            <input
              id="exp-amount"
              type="number"
              min={0.01}
              step={0.01}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              required
            />
          </div>
          <div className="form-row">
            <label htmlFor="exp-payer">Paid by</label>
            <select id="exp-payer" value={payerId} onChange={(e) => setPayerId(e.target.value)} required>
              <option value="" disabled>
                Select…
              </option>
              {members.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="form-row">
          <label htmlFor="exp-split-mode">Who bears this cost</label>
          <select id="exp-split-mode" value={splitMode} onChange={(e) => setSplitMode(e.target.value as SplitMode)}>
            <option value="business">Business (fully attributed — e.g. a reimbursement)</option>
            <option value="split">Split with members</option>
          </select>
        </div>

        {splitMode === "split" && (
          <div className="form-row">
            <div className="muted" style={{ fontSize: 12, marginBottom: 6 }}>
              Split equally among everyone checked
            </div>
            <div className="kv">
              {members.map((m) => (
                <label key={m.id} style={{ display: "flex", gap: 6, alignItems: "center" }}>
                  <input type="checkbox" checked={splitWith.has(m.id)} onChange={() => toggleSplitMember(m.id)} />
                  {m.name}
                </label>
              ))}
            </div>
          </div>
        )}

        <button className="primary" type="submit" disabled={submitting}>
          {submitting ? "Logging…" : "Log expense"}
        </button>
      </form>

      {loading ? (
        <p className="muted">Loading…</p>
      ) : entries.length === 0 ? (
        <p className="empty">No expenses logged yet.</p>
      ) : (
        <div className="table-wrap">
          <table className="ot-table">
            <thead>
              <tr>
                <th>Description</th>
                <th>Amount</th>
                <th>Paid by</th>
                <th>Split</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e) => (
                <tr key={e.id}>
                  <td className="cell-title">{e.description}</td>
                  <td className="cell-order mono">₹{e.amount.toFixed(2)}</td>
                  <td className="cell-subtitle">{memberName(e.payerId)}</td>
                  <td className="cell-type">{shareSummary(e)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
