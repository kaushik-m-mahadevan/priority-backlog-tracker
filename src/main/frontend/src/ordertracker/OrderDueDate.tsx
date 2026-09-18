import { daysUntil, formatDate } from "../lib/format";
import type { OrderStatus } from "./types";

const DONE_STATUSES: OrderStatus[] = ["SHIPPED", "DELIVERED", "CANCELLED"];

/** The due-date column/value used across Orders, My Work, and an order's own Cost & Time
 *  section — shows the same red "!" treatment Priority Backlog Tracker's own due-date
 *  marker uses once a date is overdue, instead of an overdue order looking identical to
 *  one due next month (round 5 review finding). Never flagged once an order has actually
 *  shipped/delivered/cancelled — a past due date on a finished order isn't a problem. */
export function OrderDueDate({ iso, status }: { iso: string | null; status: OrderStatus }) {
  const overdue = !DONE_STATUSES.includes(status) && (daysUntil(iso) ?? 1) <= 0;
  if (!overdue) {
    return <>{formatDate(iso)}</>;
  }
  return (
    <span className="due" style={{ display: "inline", minWidth: 0, textAlign: "inherit" }}>
      <span className="bang" title="Overdue">
        <span className="b">!</span>
        {formatDate(iso)}
      </span>
    </span>
  );
}
