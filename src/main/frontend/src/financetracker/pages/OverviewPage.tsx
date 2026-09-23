import { useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import Connections from "../../components/Connections";
import { financeTrackerApi } from "../api";
import { useFinanceGroup } from "../FinanceGroupContext";
import { formatMoney } from "../../lib/format";
import type { MemberBalanceView } from "../types";

/** ad-1: each member's live net standing — sum of every ledger row crediting them minus
 *  every row debiting them (see the backend's MemberBalanceView doc comment). There's no
 *  separate "settle up" action anymore: paying someone back is just an ordinary ledger
 *  entry (Debit the payer, Credit the payee) logged from the Ledger page, same as any
 *  other real transaction — full transparency, every member sees everyone's balance. */
export default function OverviewPage() {
  const { currentFinanceGroup, currentGroupId } = useFinanceGroup();
  const { user } = useAuth();
  const [balances, setBalances] = useState<MemberBalanceView[]>([]);
  const [loading, setLoading] = useState(true);

  const members = currentFinanceGroup?.members ?? [];
  const memberName = (id: string) => members.find((m) => m.id === id)?.name ?? "Unknown";

  const load = () => {
    if (!currentGroupId) return;
    setLoading(true);
    financeTrackerApi.balances(currentGroupId).then(setBalances).finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  return (
    <div>
      <h1 className="page-title">Overview</h1>
      <div className="toolbar" style={{ marginBottom: 8 }}>
        <p className="page-sub" style={{ margin: 0 }}>{currentFinanceGroup?.name}</p>
        <span className="spacer" />
        <Connections appletKey="financetracker" groupId={currentGroupId} />
      </div>

      <div className="card">
        <h2>Balances</h2>
        {loading ? (
          <p className="muted">Loading…</p>
        ) : balances.length === 0 ? (
          <p className="empty">Nothing logged yet — balances will show up here once a ledger entry exists.</p>
        ) : (
          <div className="table-wrap">
            <table className="ot-table">
              <thead>
                <tr>
                  <th>Person</th>
                  <th>Net</th>
                </tr>
              </thead>
              <tbody>
                {balances.map((b) => {
                  const isMe = b.userId === user?.id;
                  return (
                    <tr key={b.userId}>
                      <td className="cell-title">{isMe ? "You" : memberName(b.userId)}</td>
                      <td className="cell-order mono" style={b.net < 0 ? { color: "var(--urgent)" } : undefined}>
                        {formatMoney(Math.abs(b.net))}
                        <span className="muted"> {b.net < 0 ? "owed" : b.net > 0 ? "owed to them" : ""}</span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <p className="hint" style={{ marginTop: 10 }}>
          Positive means the ledger currently shows this person as net owed; negative means they owe. To settle
          up, log a real payment on the Ledger page (Debit the payer, Credit the payee) — same as any other
          transaction.
        </p>
      </div>
    </div>
  );
}
