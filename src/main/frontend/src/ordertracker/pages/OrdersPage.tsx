import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import type { Customer, OrderView } from "../types";

export default function OrdersPage() {
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const [ordersList, setOrdersList] = useState<OrderView[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<"ALL" | "INDIVIDUAL" | "BULK">("ALL");

  useEffect(() => {
    setLoading(true);
    Promise.all([orderTrackerApi.orders(groupId), orderTrackerApi.customers(groupId)])
      .then(([o, c]) => {
        setOrdersList(o);
        setCustomers(c);
      })
      .finally(() => setLoading(false));
  }, [groupId]);

  const customerName = (id: string) => customers.find((c) => c.id === id)?.name ?? "—";
  const visible = ordersList.filter((o) => filter === "ALL" || o.orderType === filter);

  return (
    <div>
      <div className="toolbar">
        <h1 className="page-title" style={{ marginBottom: 0 }}>
          Orders
        </h1>
        <span className="spacer" />
        <select value={filter} onChange={(e) => setFilter(e.target.value as typeof filter)}>
          <option value="ALL">All orders</option>
          <option value="INDIVIDUAL">Individual</option>
          <option value="BULK">Bulk</option>
        </select>
        <Link to="/ordertracker/orders/new" className="primary">
          + New order
        </Link>
      </div>

      {loading ? (
        <p className="muted">Loading…</p>
      ) : visible.length === 0 ? (
        <p className="empty">No orders yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Order #</th>
                <th>Item</th>
                <th>Customer</th>
                <th>Type</th>
                <th>Status</th>
                <th>Completion</th>
                <th>Due (est.)</th>
                <th>Payment</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((o) => (
                <tr key={o.id}>
                  <td className="mono">
                    <Link to={`/ordertracker/orders/${o.id}`}>{o.orderNumber}</Link>
                  </td>
                  <td>{o.itemName || <span className="muted">—</span>}</td>
                  <td>{customerName(o.customerId)}</td>
                  <td>
                    <span className="badge">{o.orderType}</span>
                  </td>
                  <td>
                    <span className="badge">{o.status}</span>
                  </td>
                  <td>{o.completionPercentage.toFixed(0)}%</td>
                  <td>
                    {(() => {
                      const due = o.orderType === "INDIVIDUAL" ? o.costEstimate?.computedDueDate : o.bulkDetails?.computedDueDate;
                      return due ? new Date(due).toLocaleDateString() : <span className="muted">—</span>;
                    })()}
                  </td>
                  <td>
                    <span className="badge">{o.paymentStatus}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
