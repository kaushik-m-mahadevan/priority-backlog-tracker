import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AsyncSection } from "../../components/AsyncSection";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import { OrderDueDate } from "../OrderDueDate";
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
                <th>Due (est.)</th>
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
                      iso={(o.orderType === "INDIVIDUAL" ? o.costEstimate?.computedDueDate : o.bulkDetails?.computedDueDate) ?? null}
                      status={o.status}
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
    </div>
  );
}
