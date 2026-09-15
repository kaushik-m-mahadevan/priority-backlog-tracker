import { useEffect, useState } from "react";
import { financeTrackerApi } from "../api";
import { useFinanceGroup } from "../FinanceGroupContext";
import type { LedgerEntryView } from "../types";

type CreditedTo = "business" | "person";

/** Income (an order payment, an investment from a specific person) reuses the same
 *  LedgerEntry ledger as expenses (type: INCOME) - no new backend, just this form. Unlike
 *  an expense's "split with several members", income here is credited to exactly one
 *  party: the business as a whole, or one specific person (e.g. crediting an investor's
 *  contribution) - splitting incoming money across several people isn't a real scenario
 *  this app needs, so the UI doesn't offer it. */
export default function IncomePage() {
  const { currentFinanceGroup, currentGroupId } = useFinanceGroup();
  const [entries, setEntries] = useState<LedgerEntryView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [receivedById, setReceivedById] = useState("");
  const [creditedTo, setCreditedTo] = useState<CreditedTo>("business");
  const [creditedPersonId, setCreditedPersonId] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const members = currentFinanceGroup?.members ?? [];
  const memberName = (id: string) => members.find((m) => m.id === id)?.name ?? "Unknown";

  const load = () => {
    if (!currentGroupId) return;
    setLoading(true);
    financeTrackerApi
      .ledgerEntries(currentGroupId)
      .then((all) => setEntries(all.filter((e) => e.type === "INCOME")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const amt = Number(amount);
    if (!description.trim() || !amt || amt <= 0 || !receivedById || !currentGroupId) return;
    if (creditedTo === "person" && !creditedPersonId) {
      setError("Pick who this income is credited to");
      return;
    }

    setSubmitting(true);
    try {
      await financeTrackerApi.logLedgerEntry(currentGroupId, {
        type: "INCOME",
        description: description.trim(),
        amount: amt,
        payerId: receivedById,
        shares:
          creditedTo === "business"
            ? [{ partyType: "BUSINESS", personId: null, ratio: 1 }]
            : [{ partyType: "PERSON", personId: creditedPersonId, ratio: 1 }],
      });
      setDescription("");
      setAmount("");
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to log income");
    } finally {
      setSubmitting(false);
    }
  };

  const creditSummary = (entry: LedgerEntryView) => {
    const s = entry.shares[0];
    if (!s || s.partyType === "BUSINESS") return "Business";
    return s.personId ? memberName(s.personId) : "Business";
  };

  return (
    <div>
      <h1 className="page-title">Income</h1>
      <p className="page-sub">{currentFinanceGroup?.name}</p>

      <form className="card" onSubmit={submit} style={{ marginBottom: 16 }}>
        {error && <div className="error">{error}</div>}
        <div className="form-row">
          <label htmlFor="inc-description">Description</label>
          <input
            id="inc-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="e.g. Payment for order #123, or an investment"
            required
          />
        </div>
        <div className="form-grid">
          <div className="form-row">
            <label htmlFor="inc-amount">Amount</label>
            <input
              id="inc-amount"
              type="number"
              min={0.01}
              step={0.01}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              required
            />
          </div>
          <div className="form-row">
            <label htmlFor="inc-received-by">Received by</label>
            <select id="inc-received-by" value={receivedById} onChange={(e) => setReceivedById(e.target.value)} required>
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

        <div className="form-grid">
          <div className="form-row">
            <label htmlFor="inc-credited-to">Credited to</label>
            <select id="inc-credited-to" value={creditedTo} onChange={(e) => setCreditedTo(e.target.value as CreditedTo)}>
              <option value="business">Business (e.g. an order payment)</option>
              <option value="person">A specific person (e.g. an investment)</option>
            </select>
          </div>
          {creditedTo === "person" && (
            <div className="form-row">
              <label htmlFor="inc-credited-person">Person</label>
              <select
                id="inc-credited-person"
                value={creditedPersonId}
                onChange={(e) => setCreditedPersonId(e.target.value)}
                required
              >
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
          )}
        </div>

        <button className="primary" type="submit" disabled={submitting}>
          {submitting ? "Logging…" : "Log income"}
        </button>
      </form>

      {loading ? (
        <p className="muted">Loading…</p>
      ) : entries.length === 0 ? (
        <p className="empty">No income logged yet.</p>
      ) : (
        <div className="table-wrap">
          <table className="ot-table">
            <thead>
              <tr>
                <th>Description</th>
                <th>Amount</th>
                <th>Received by</th>
                <th>Credited to</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e) => (
                <tr key={e.id}>
                  <td className="cell-title">{e.description}</td>
                  <td className="cell-order mono">₹{e.amount.toFixed(2)}</td>
                  <td className="cell-subtitle">{memberName(e.payerId)}</td>
                  <td className="cell-type">{creditSummary(e)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
