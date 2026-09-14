import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import type { Customer, OrderView } from "../types";

type CompletionFilter = "pending" | "done" | "all";

export default function MyWorkPage() {
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const [ordersList, setOrdersList] = useState<OrderView[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(true);
  const [completionFilter, setCompletionFilter] = useState<CompletionFilter>("pending");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  const load = () => {
    setLoading(true);
    Promise.all([
      orderTrackerApi.myWork(groupId, {
        status: completionFilter,
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
      }),
      orderTrackerApi.customers(groupId),
    ])
      .then(([o, c]) => {
        setOrdersList(o);
        setCustomers(c);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId, completionFilter, from, to]);

  const customerName = (id: string) => customers.find((c) => c.id === id)?.name ?? "—";

  return (
    <div>
      <div className="toolbar">
        <h1 className="page-title" style={{ marginBottom: 0 }}>
          My Work
        </h1>
        <span className="spacer" />
        <select aria-label="Completion filter" value={completionFilter} onChange={(e) => setCompletionFilter(e.target.value as CompletionFilter)}>
          <option value="pending">Pending</option>
          <option value="done">Done</option>
          <option value="all">All</option>
        </select>
        <input aria-label="From date" type="date" value={from} onChange={(e) => setFrom(e.target.value)} title="From" />
        <input aria-label="To date" type="date" value={to} onChange={(e) => setTo(e.target.value)} title="To" />
      </div>

      {loading ? (
        <p className="muted">Loading…</p>
      ) : ordersList.length === 0 ? (
        <p className="empty">
          {completionFilter === "pending" ? "No pending work assigned to you." : "No orders match these filters."}
        </p>
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
                <th>Due</th>
              </tr>
            </thead>
            <tbody>
              {ordersList.map((o) => (
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
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
