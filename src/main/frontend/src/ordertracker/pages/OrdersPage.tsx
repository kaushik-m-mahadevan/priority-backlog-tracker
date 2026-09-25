import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AsyncSection } from "../../components/AsyncSection";
import Connections from "../../components/Connections";
import { SlideToggle } from "../../components/SlideToggle";
import { useSwipe } from "../../lib/useSwipe";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import CancelOrderModal from "../CancelOrderModal";
import { OrderDueDate } from "../OrderDueDate";
import { COLUMNS, COLUMN_LABELS, columnOf, legalMoves, nextColumnFirstStatus } from "../orderStatusColumns";
import type { Customer, OrderView } from "../types";

/** ad-3's board view. Only the 3 active columns (Pending, In Progress, Completed) render
 *  here — Delivered and Cancelled orders are terminal and "drop off" the board per the
 *  design. This codebase doesn't have a separate historic-orders screen (out of scope for
 *  this pass), so the existing List view below is what still shows every order, terminal
 *  ones included — it's the one place to find a Delivered or Cancelled order once it's
 *  off the board. */
const BOARD_COLUMNS = COLUMNS.filter((c) => c !== "CLOSED");

export default function OrdersPage() {
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const [ordersList, setOrdersList] = useState<OrderView[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<"ALL" | "INDIVIDUAL" | "BULK">("ALL");
  const [view, setView] = useState<"BOARD" | "LIST">("BOARD");
  const [actionError, setActionError] = useState<string | null>(null);
  const [moving, setMoving] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<OrderView | null>(null);

  const load = () => {
    setLoading(true);
    return Promise.all([orderTrackerApi.orders(groupId), orderTrackerApi.customers(groupId)])
      .then(([o, c]) => {
        setOrdersList(o);
        setCustomers(c);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId]);

  const customerName = (id: string) => customers.find((c) => c.id === id)?.name ?? "—";
  const visible = ordersList.filter((o) => filter === "ALL" || o.orderType === filter);
  const boardVisible = visible.filter((o) => columnOf(o.status) !== null && columnOf(o.status) !== "CLOSED");

  const move = async (order: OrderView, target: string, needsJustification: boolean) => {
    let justification: string | undefined;
    if (needsJustification) {
      const input = window.prompt(
        `Moving "${order.itemName || order.orderNumber}" back to ${target.replace(/_/g, " ")} — what happened? ` +
          `(e.g. Item damaged, Rework needed, Customer changed request)`
      );
      if (!input || !input.trim()) return;
      justification = input.trim();
    }
    setActionError(null);
    setMoving(order.id);
    try {
      const updated = await orderTrackerApi.updateStatus(groupId, order.id, target, justification);
      setOrdersList((prev) => prev.map((o) => (o.id === updated.id ? updated : o)));
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Failed to move this order");
    } finally {
      setMoving(null);
    }
  };

  return (
    <div>
      <div className="toolbar">
        <h1 className="page-title" style={{ marginBottom: 0 }}>
          Orders
        </h1>
        <span className="spacer" />
        <SlideToggle
          value={view}
          options={[
            { value: "BOARD", label: "Board" },
            { value: "LIST", label: "List" },
          ]}
          onChange={setView}
        />
        <select value={filter} onChange={(e) => setFilter(e.target.value as typeof filter)}>
          <option value="ALL">All orders</option>
          <option value="INDIVIDUAL">Individual</option>
          <option value="BULK">Bulk</option>
        </select>
        <Link to="/ordertracker/orders/new" className="primary">
          + New order
        </Link>
        <Connections appletKey="ordertracker" groupId={groupId} />
      </div>

      {actionError && <div className="error">{actionError}</div>}

      {view === "BOARD" ? (
        <AsyncSection loading={loading} isEmpty={boardVisible.length === 0} empty={<p className="empty">No active orders yet.</p>}>
          <div className="board">
            {BOARD_COLUMNS.map((col) => {
              const cardsInColumn = boardVisible.filter((o) => columnOf(o.status) === col);
              return (
                <div key={col} className="board-column">
                  <div className="board-column-head">
                    <span>{COLUMN_LABELS[col]}</span>
                    <span className="board-column-count">{cardsInColumn.length}</span>
                  </div>
                  <div className="board-column-body">
                    {cardsInColumn.map((o) => (
                      <BoardCard
                        key={o.id}
                        order={o}
                        customerName={customerName(o.customerId)}
                        moving={moving === o.id}
                        onMove={move}
                        onSwipeCancel={() => setCancelling(o)}
                      />
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </AsyncSection>
      ) : (
        <AsyncSection loading={loading} isEmpty={visible.length === 0} empty={<p className="empty">No orders yet.</p>}>
          <div className="table-wrap">
            <table className="ot-table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Order #</th>
                  <th>Customer</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Completion</th>
                  <th>Due</th>
                  <th>Payment</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((o) => (
                  <tr key={o.id}>
                    <td className="cell-title">
                      <Link to={`/ordertracker/orders/${o.id}`}>{o.itemName || <span className="muted">Untitled order</span>}</Link>
                    </td>
                    <td className="cell-order mono">{o.orderNumber}</td>
                    <td className="cell-subtitle">{customerName(o.customerId)}</td>
                    <td className="cell-type">
                      <span className="badge">{o.orderType}</span>
                    </td>
                    <td className="cell-status">
                      <span className="badge">{o.status.replace(/_/g, " ")}</span>
                    </td>
                    <td className="cell-completion">{o.completionPercentage.toFixed(0)}%</td>
                    <td className="cell-due">
                      <OrderDueDate
                        iso={o.quotedDeliveryDate ?? (o.orderType === "INDIVIDUAL" ? o.costEstimate?.computedDueDate : o.bulkDetails?.computedDueDate) ?? null}
                        status={o.status}
                        estimateIso={(o.orderType === "INDIVIDUAL" ? o.costEstimate?.computedDueDate : o.bulkDetails?.computedDueDate) ?? null}
                      />
                    </td>
                    <td className="cell-payment">
                      <span className="badge">{o.paymentStatus.replace(/_/g, " ")}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </AsyncSection>
      )}

      {cancelling && (
        <CancelOrderModal
          groupId={groupId}
          order={cancelling}
          onCancelled={(updated) => {
            setOrdersList((prev) => prev.map((o) => (o.id === updated.id ? updated : o)));
          }}
          onClose={() => setCancelling(null)}
        />
      )}
    </div>
  );
}

/** ui-9: a board card with swipe support — swipe left opens the guided cancel flow, swipe
 *  right promotes to the first status of the next adjacent column (a no-op, ignored, if
 *  there is no next column to promote into). Touch-only gesture; the existing "Move to…"
 *  dropdown remains the non-touch, always-available way to change status. Split out from
 *  the board's map loop because useSwipe is a hook and needs one call per card. */
function BoardCard({
  order: o,
  customerName,
  moving,
  onMove,
  onSwipeCancel,
}: {
  order: OrderView;
  customerName: string;
  moving: boolean;
  onMove: (order: OrderView, target: string, needsJustification: boolean) => void;
  onSwipeCancel: () => void;
}) {
  const moves = legalMoves(o.status);
  const promoteTarget = nextColumnFirstStatus(o.status);
  const { offset, onTouchStart, onTouchMove, onTouchEnd } = useSwipe(
    () => onSwipeCancel(),
    promoteTarget ? () => onMove(o, promoteTarget, false) : undefined
  );

  return (
    <div
      className="board-card"
      style={offset !== 0 ? { transform: `translateX(${offset}px)`, transition: "none" } : undefined}
      onTouchStart={onTouchStart}
      onTouchMove={onTouchMove}
      onTouchEnd={onTouchEnd}
    >
      <Link to={`/ordertracker/orders/${o.id}`} className="board-card-title">
        {o.itemName || <span className="muted">Untitled order</span>}
      </Link>
      <div className="board-card-meta">
        <span className="mono">{o.orderNumber}</span>
        <span>{customerName}</span>
      </div>
      <div className="board-card-badges">
        <span className="badge">{o.orderType}</span>
        <span className="badge">{o.status.replace(/_/g, " ")}</span>
        <span className="badge">{o.paymentStatus.replace(/_/g, " ")}</span>
      </div>
      <div className="board-card-due">
        <OrderDueDate
          iso={o.quotedDeliveryDate ?? (o.orderType === "INDIVIDUAL" ? o.costEstimate?.computedDueDate : o.bulkDetails?.computedDueDate) ?? null}
          status={o.status}
          estimateIso={(o.orderType === "INDIVIDUAL" ? o.costEstimate?.computedDueDate : o.bulkDetails?.computedDueDate) ?? null}
        />
      </div>
      {moves.length > 0 && (
        <select
          className="board-card-move"
          disabled={moving}
          value={o.status}
          onChange={(e) => {
            const target = e.target.value;
            if (target === o.status) return;
            const m = moves.find((mv) => mv.status === target);
            onMove(o, target, m?.needsJustification ?? false);
            e.target.value = o.status;
          }}
        >
          <option value={o.status}>Move to…</option>
          {moves.map((m) => (
            <option key={m.status} value={m.status}>
              {m.status.replace(/_/g, " ")}
              {m.needsJustification ? " (back)" : ""}
            </option>
          ))}
        </select>
      )}
    </div>
  );
}
