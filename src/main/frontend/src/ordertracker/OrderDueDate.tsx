import { daysUntil, formatDate } from "../lib/format";
import type { OrderStatus } from "./types";

const DONE_STATUSES: OrderStatus[] = ["SHIPPED", "DELIVERED", "CANCELLED"];

/** The due-date column/value used across Orders, My Work, and an order's own Cost & Time
 *  section — shows the same red "!" treatment Priority Backlog Tracker's own due-date
 *  marker uses once a date is overdue, instead of an overdue order looking identical to
 *  one due next month (round 5 review finding). Never flagged once an order has actually
 *  shipped/delivered/cancelled — a past due date on a finished order isn't a problem.
 *
 *  {@code quotedIso}, when passed, flags the same way if the estimate lands later than what
 *  was quoted to the customer — the estimate itself can be months away (so never "overdue"
 *  by itself) while still conflicting with a much sooner promise (list-view counterpart of
 *  the warning already shown on the order's own Cost & Time section, mb-10). */
export function OrderDueDate({ iso, status, quotedIso }: { iso: string | null; status: OrderStatus; quotedIso?: string | null }) {
  const live = !DONE_STATUSES.includes(status);
  const overdue = live && (daysUntil(iso) ?? 1) <= 0;
  const conflictsWithQuote = live && !overdue && !!iso && !!quotedIso && new Date(iso) > new Date(quotedIso);
  if (!overdue && !conflictsWithQuote) {
    return <>{formatDate(iso)}</>;
  }
  return (
    <span className="due" style={{ display: "inline", minWidth: 0, textAlign: "inherit" }}>
      <span className="bang" title={overdue ? "Overdue" : "Later than the date quoted to the customer"}>
        <span className="b">!</span>
        {formatDate(iso)}
      </span>
    </span>
  );
}
