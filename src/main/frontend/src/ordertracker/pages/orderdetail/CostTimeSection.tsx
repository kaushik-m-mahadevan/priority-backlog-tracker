import { formatMoney } from "../../../lib/format";
import { OrderDueDate } from "../../OrderDueDate";
import { DELIVERY_TIER_LABELS } from "./deliveryTiers";
import type { BusinessConfig, OrderView } from "../../types";

/** Extracted from OrderDetailPage.tsx (fdup-1) — fully self-contained via props, no
 *  closure over the parent's state. Pure display, no mutations. */
export function CostTimeSection({ order, config }: { order: OrderView; config: BusinessConfig }) {
  const dueDate = order.orderType === "INDIVIDUAL" ? order.costEstimate?.computedDueDate : order.bulkDetails?.computedDueDate;

  return (
    <div className="card cost-card" style={{ background: "var(--bg-elev-2)" }}>
      <h2>Cost &amp; time — estimate</h2>
      <p className="muted" style={{ fontSize: 12, marginTop: -4, marginBottom: 10 }}>
        Calculated from what's entered above. Not a final invoice — record the real numbers once the order ships.
      </p>
      {order.orderType === "INDIVIDUAL" && order.costEstimate ? (
        <>
          {config && !config.hourlyWageConfirmed && (
            <p className="hint" style={{ marginTop: 0 }}>
              ⚠ Labor is priced at the default rate ({config.currency} {config.hourlyWage}/h) — confirm your
              business's real rate in Business Settings.
            </p>
          )}
          {order.costEstimate.itemizedBreakdown.map((b) => (
            <div className="row" key={b.label}><span className="k">{b.label}</span><span className="v">{formatMoney(b.amount)}</span></div>
          ))}
          <div className="cost-total"><span className="k">Estimated price</span><span className="v">{formatMoney(order.costEstimate.finalCost)}</span></div>
          <div className="row" style={{ marginTop: 8 }}><span className="k">Estimated time</span><span className="v">{order.costEstimate.grossTimeHours}h</span></div>
        </>
      ) : (
        <>
          <div className="row"><span className="k">Total quantity</span><span className="v">{order.bulkDetails?.totalQuantity}</span></div>
          <div className="cost-total"><span className="k">Estimated total cost</span><span className="v">{formatMoney(order.bulkDetails?.totalFinalCost ?? 0)}</span></div>
          <div className="row" style={{ marginTop: 8 }}><span className="k">Estimated total time</span><span className="v">{order.bulkDetails?.totalTimeHours}h</span></div>
        </>
      )}

      <div className="row" style={{ marginTop: 12 }}><span className="k">Shipping to</span>
        <span className="v">{DELIVERY_TIER_LABELS[order.deliveryTier]}</span></div>
      {order.orderType === "INDIVIDUAL" && order.costEstimate && (
        <>
          <div className="row"><span className="k">Work days</span>
            <span className="v">{order.costEstimate.workDays}d (allocated {order.costEstimate.grossTimeHours}h ÷ hours/day)</span></div>
          <div className="row"><span className="k">Delivery buffer</span>
            <span className="v">+{order.costEstimate.deliveryBufferDays}d</span></div>
          {config && (
            <div className="row"><span className="k">Time overhead</span>
              <span className="v">×{(1 + config.overheadPercentage).toFixed(2)}</span></div>
          )}
        </>
      )}
      <div className="row"><span className="k">Estimated delivery</span>
        <span className="v"><OrderDueDate iso={dueDate ?? null} status={order.status} /></span></div>
      {order.quotedDeliveryDate && (
        <div className="row"><span className="k">Quoted to customer</span>
          <span className="v"><OrderDueDate iso={order.quotedDeliveryDate} status={order.status} /></span></div>
      )}
      {dueDate && order.quotedDeliveryDate && new Date(dueDate) > new Date(order.quotedDeliveryDate) && (
        <p className="hint bad" style={{ marginTop: 6 }}>
          Estimated delivery is later than what was quoted to the customer.
        </p>
      )}
    </div>
  );
}
