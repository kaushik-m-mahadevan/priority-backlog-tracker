import { daysUntil, formatDate } from "../lib/format";
import type { OrderStatus } from "./types";

const DONE_STATUSES: OrderStatus[] = ["SHIPPED", "DELIVERED", "CANCELLED"];

/** The due-date column/value used across Orders, My Work, Customer detail, and an order's
 *  own Cost & Time section. {@code iso} is the real deadline — the date quoted to the
 *  customer, since that's what the business is actually held to once an order is quoted,
 *  not the internal cost/time estimate (which only ever mattered for arriving at that quote
 *  in the first place). Shows the same red "!" treatment Priority Backlog Tracker's own
 *  due-date marker uses once {@code iso} is overdue (round 5 review finding). Never flagged
 *  once an order has actually shipped/delivered/cancelled — a past due date on a finished
 *  order isn't a problem.
 *
 *  {@code estimateIso}, when passed, flags the same way if the internal estimate has since
 *  drifted later than the quoted date — an early warning the business is trending to miss
 *  its own promise, even though the quote (not the estimate) is what's actually displayed. */
export function OrderDueDate({ iso, status, estimateIso }: { iso: string | null; status: OrderStatus; estimateIso?: string | null }) {
  const live = !DONE_STATUSES.includes(status);
  const overdue = live && (daysUntil(iso) ?? 1) <= 0;
  const conflictsWithEstimate = live && !overdue && !!iso && !!estimateIso && new Date(estimateIso) > new Date(iso);
  if (!overdue && !conflictsWithEstimate) {
    return <>{formatDate(iso)}</>;
  }
  return (
    <span className="due" style={{ display: "inline", minWidth: 0, textAlign: "inherit" }}>
      <span className="bang" title={overdue ? "Overdue" : "The internal estimate is now later than the date quoted to the customer"}>
        <span className="b">!</span>
        {formatDate(iso)}
      </span>
    </span>
  );
}
