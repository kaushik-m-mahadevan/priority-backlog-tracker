import { useState } from "react";
import { formatMoney } from "../../../lib/format";
import { orderTrackerApi } from "../../api";
import { creatorName } from "./creatorName";
import { ShippingCard } from "./ShippingCard";
import type { Creator, OrderView, PaymentType } from "../../types";

/** Extracted from OrderDetailPage.tsx (fdup-1) — fully self-contained via props, no
 *  closure over the parent's state. Payment recording/removal plus the existing
 *  ShippingCard, grouped together since both live under the "Logistics" section. */
export function LogisticsSection({
  groupId,
  order,
  creators,
  onUpdated,
}: {
  groupId: string;
  order: OrderView;
  creators: Creator[];
  onUpdated: (o: OrderView) => void;
}) {
  const [error, setError] = useState<string | null>(null);
  const [paymentAmount, setPaymentAmount] = useState(0);
  const [paymentType, setPaymentType] = useState<PaymentType>("ADVANCE");
  const [paymentMode, setPaymentMode] = useState("UPI");
  const [paymentModeOther, setPaymentModeOther] = useState("");
  const [paymentReceivedBy, setPaymentReceivedBy] = useState("");

  const runAction = async (fn: () => Promise<OrderView>): Promise<boolean> => {
    setError(null);
    try {
      onUpdated(await fn());
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "That action failed — please retry.");
      return false;
    }
  };

  return (
    <>
      <div className="card">
        <h2>Payment</h2>
        {error && <div className="error">{error}</div>}
        {order.payments.length === 0 ? (
          <p className="empty">No payments yet.</p>
        ) : (
          order.payments.map((p) => (
            <div className="row" key={p.paymentId}>
              <span className="k">{p.type}</span>
              <span className="v">
                {formatMoney(p.amount)} ({p.mode})
                <button
                  type="button"
                  className="linkbtn"
                  style={{ marginLeft: 8, fontSize: 12 }}
                  onClick={() => {
                    if (window.confirm(`Remove this ${formatMoney(p.amount)} ${p.type.toLowerCase()} payment?`)) {
                      runAction(() => orderTrackerApi.removePayment(groupId, order.id, p.paymentId));
                    }
                  }}
                >
                  remove
                </button>
              </span>
            </div>
          ))
        )}
        <div className="row">
          <span className="k">Balance</span>
          {order.balanceAmount < 0 ? (
            <span className="v" style={{ color: "var(--urgent)", fontWeight: 600 }}>
              Over budget by {formatMoney(Math.abs(order.balanceAmount))}
            </span>
          ) : (
            <span className="v">{formatMoney(order.balanceAmount)}</span>
          )}
        </div>
        <div className="toolbar" style={{ marginTop: 10 }}>
          <select aria-label="Payment type" value={paymentType} onChange={(e) => setPaymentType(e.target.value as PaymentType)}>
            <option value="ADVANCE">Advance</option>
            <option value="INSTALLMENT">Installment</option>
            <option value="FINAL">Final</option>
            <option value="REFUND">Refund</option>
          </select>
          <input aria-label="Payment amount" type="number" placeholder="Amount" value={paymentAmount || ""} onChange={(e) => setPaymentAmount(Number(e.target.value))} />
          <select aria-label="Payment mode" value={paymentMode} onChange={(e) => setPaymentMode(e.target.value)}>
            <option value="UPI">UPI</option>
            <option value="CASH">Cash</option>
            <option value="BANK_TRANSFER">Bank transfer</option>
            <option value="CARD">Card</option>
            <option value="OTHER">Other</option>
          </select>
          {paymentMode === "OTHER" && (
            <input aria-label="Payment mode (other)" placeholder="Describe how" value={paymentModeOther}
              onChange={(e) => setPaymentModeOther(e.target.value)} />
          )}
          <select aria-label="Received by" value={paymentReceivedBy} onChange={(e) => setPaymentReceivedBy(e.target.value)}>
            <option value="">Received by: me</option>
            {creators.map((c) => (
              <option key={c.id} value={c.userId}>{creatorName(creators, c.id)}</option>
            ))}
            <option value="BUSINESS">Business Account</option>
          </select>
          <button
            className="primary"
            disabled={paymentMode === "OTHER" && !paymentModeOther.trim()}
            onClick={async () => {
              if (!paymentAmount) return;
              const mode = paymentMode === "OTHER" ? paymentModeOther.trim() : paymentMode;
              const ok = await runAction(() => orderTrackerApi.addPayment(groupId, order.id, {
                type: paymentType, amount: paymentAmount, mode,
                receivedBy: paymentReceivedBy || undefined,
              }));
              if (ok) {
                setPaymentAmount(0);
                setPaymentModeOther("");
                setPaymentReceivedBy("");
              }
            }}
          >
            Record
          </button>
        </div>
      </div>

      <ShippingCard groupId={groupId} order={order} onUpdated={onUpdated} />
    </>
  );
}
