import { useEffect, useState } from "react";
import { useAuth } from "../../../auth/AuthContext";
import { formatDate, formatMoney } from "../../../lib/format";
import { orderTrackerApi } from "../../api";
import type { OrderFinalizationView, OrderView } from "../../types";

/** Extracted from OrderDetailPage.tsx (fdup-1) — fully self-contained via props, no
 *  closure over the parent's state. Every current group member must unanimously accept
 *  an order's final actual cost/revenue before it's treated as settled (same consensus
 *  mechanics as the business's cost-config change requests). Cost/revenue default to the
 *  computed estimate and amount collected so far, adjustable before proposing. */
export function FinalizationCard({ groupId, order }: { groupId: string; order: OrderView }) {
  const { user } = useAuth();
  const [finalization, setFinalization] = useState<OrderFinalizationView | null>(null);
  const [finalCost, setFinalCost] = useState(0);
  const [finalRevenue, setFinalRevenue] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const estimatedCost = order.orderType === "INDIVIDUAL"
    ? order.costEstimate?.finalCost ?? 0
    : order.bulkDetails?.totalFinalCost ?? 0;

  const load = () => {
    orderTrackerApi.orderFinalization(groupId, order.id).then((f) => {
      setFinalization(f);
      if (f.status === "NONE") {
        setFinalCost(estimatedCost);
        setFinalRevenue(order.netPaid);
      }
    });
  };

  useEffect(load, [groupId, order.id]);

  if (!finalization) return <p className="muted">Loading…</p>;

  const respond = async (approve: boolean) => {
    setError(null);
    try {
      setFinalization(
        approve
          ? await orderTrackerApi.approveOrderFinalization(groupId, order.id)
          : await orderTrackerApi.rejectOrderFinalization(groupId, order.id)
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to respond");
    }
  };

  const propose = async () => {
    setError(null);
    try {
      setFinalization(await orderTrackerApi.proposeOrderFinalization(groupId, order.id, finalCost, finalRevenue));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to propose");
    }
  };

  return (
    <div className="card">
      <h2>Order finalization</h2>
      <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
        Locks in the order's real cost and revenue once every group member accepts them — used later for profit
        distribution.
      </p>
      {error && <div className="error">{error}</div>}

      {finalization.status === "FINALIZED" && (
        <>
          <div className="row"><span className="k">Final cost</span><span className="v">{formatMoney(finalization.finalCost)}</span></div>
          <div className="row"><span className="k">Final revenue</span><span className="v">{formatMoney(finalization.finalRevenue)}</span></div>
          <div className="cost-total"><span className="k">Final profit</span><span className="v">{formatMoney(finalization.finalProfit)}</span></div>
          <p className="muted" style={{ fontSize: 12 }}>Finalized {formatDate(finalization.finalizedAt)}.</p>
        </>
      )}

      {finalization.status === "PENDING" && (
        <div className="card" style={{ background: "var(--bg-elev-2)" }}>
          <div className="row"><span className="k">Proposed cost</span><span className="v">{formatMoney(finalization.finalCost)}</span></div>
          <div className="row"><span className="k">Proposed revenue</span><span className="v">{formatMoney(finalization.finalRevenue)}</span></div>
          <div className="row"><span className="k">Proposed profit</span><span className="v">{formatMoney(finalization.finalProfit)}</span></div>
          <p className="muted" style={{ fontSize: 13 }}>
            Approved by {finalization.approvedByUserIds.length}/{finalization.groupMemberIds.length} member(s) so far.
          </p>
          {user && finalization.approvedByUserIds.includes(user.id) ? (
            <p className="hint">You've approved this — waiting on everyone else.</p>
          ) : (
            <div className="toolbar">
              <button className="primary" onClick={() => respond(true)}>Approve</button>
              <button onClick={() => respond(false)}>Reject</button>
            </div>
          )}
        </div>
      )}

      {finalization.status === "NONE" && (
        <div className="toolbar" style={{ flexWrap: "wrap" }}>
          <label>
            Final cost
            <input aria-label="Final cost" type="number" value={finalCost} onChange={(e) => setFinalCost(Number(e.target.value))} />
          </label>
          <label>
            Final revenue
            <input aria-label="Final revenue" type="number" value={finalRevenue} onChange={(e) => setFinalRevenue(Number(e.target.value))} />
          </label>
          <button className="primary" onClick={propose}>Propose finalization</button>
        </div>
      )}
    </div>
  );
}
