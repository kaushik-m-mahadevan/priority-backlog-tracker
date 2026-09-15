import { useEffect, useState } from "react";
import { financeTrackerApi } from "../api";
import { useFinanceGroup } from "../FinanceGroupContext";
import type { BalanceView } from "../types";

/** Each person's standing in the ledger, computed from every expense/income entry — see
 *  the backend's BalanceView doc comment for the exact rules. Two separate numbers on
 *  purpose: what other people owe you (or you owe them) is a different relationship from
 *  what the business itself owes you (a business-attributed expense you fronted, or an
 *  investment credited to you) — full transparency, so every member sees everyone's row,
 *  not just their own. */
export default function OverviewPage() {
  const { currentFinanceGroup, currentGroupId } = useFinanceGroup();
  const [balances, setBalances] = useState<BalanceView[]>([]);
  const [loading, setLoading] = useState(true);

  const memberName = (id: string) => currentFinanceGroup?.members.find((m) => m.id === id)?.name ?? "Unknown";

  useEffect(() => {
    if (!currentGroupId) return;
    setLoading(true);
    financeTrackerApi.balances(currentGroupId).then(setBalances).finally(() => setLoading(false));
  }, [currentGroupId]);

  const fmt = (n: number) => (n < 0 ? `-₹${Math.abs(n).toFixed(2)}` : `₹${n.toFixed(2)}`);

  return (
    <div>
      <h1 className="page-title">Overview</h1>
      <p className="page-sub">{currentFinanceGroup?.name}</p>

      <div className="card">
        <h2>Balances</h2>
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
                </tr>
              </thead>
              <tbody>
                {balances.map((b) => (
                  <tr key={b.personId}>
                    <td className="cell-title">{memberName(b.personId)}</td>
                    <td className="cell-order mono" style={b.netFromOthers < 0 ? { color: "var(--urgent)" } : undefined}>
                      {fmt(b.netFromOthers)}
                      <span className="muted"> {b.netFromOthers < 0 ? "owed" : b.netFromOthers > 0 ? "owed to them" : ""}</span>
                    </td>
                    <td className="cell-subtitle mono">{fmt(b.owedByBusiness)}</td>
                  </tr>
                ))}
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
    </div>
  );
}
