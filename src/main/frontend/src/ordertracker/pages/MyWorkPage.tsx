import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import { useLinkedYarnTypes } from "../useLinkedYarnTypes";
import { OrderDueDate } from "../OrderDueDate";
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
  const linkedYarnTypes = useLinkedYarnTypes(groupId);
  const [syncPreview, setSyncPreview] = useState<Record<string, number> | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [syncDone, setSyncDone] = useState<Record<string, number> | null>(null);

  const yarnLabel = (id: string) => {
    const y = linkedYarnTypes.find((yt) => yt.id === id);
    return y ? `${y.brand} — ${y.thickness}, ${y.colour}` : id;
  };

  const loadSyncPreview = () => {
    setSyncDone(null);
    orderTrackerApi.previewInventorySync(groupId).then(setSyncPreview);
  };

  const applySync = async () => {
    setSyncing(true);
    try {
      const applied = await orderTrackerApi.applyInventorySync(groupId);
      setSyncDone(applied);
      setSyncPreview(null);
      load();
    } finally {
      setSyncing(false);
    }
  };

  const load = () => {
    setLoading(true);
    Promise.all([
      orderTrackerApi.myWork(groupId, {
        completionFilter,
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
      {linkedYarnTypes.length > 0 && (
        <div className="card" style={{ marginBottom: 16 }}>
          <div className="toolbar">
            <h2 style={{ margin: 0 }}>Sync yarn usage to inventory</h2>
            <span className="spacer" />
            <button type="button" onClick={loadSyncPreview}>Preview</button>
          </div>
          <p className="hint" style={{ marginTop: 4 }}>
            Applies every usage entry you've logged that isn't already synced, across all your orders in this business.
          </p>
          {syncPreview && (
            Object.keys(syncPreview).length === 0 ? (
              <p className="empty">Nothing pending.</p>
            ) : (
              <div>
                <ul style={{ margin: "8px 0" }}>
                  {Object.entries(syncPreview).map(([yarnTypeId, qty]) => (
                    <li key={yarnTypeId}>{yarnLabel(yarnTypeId)}: {qty} skeins</li>
                  ))}
                </ul>
                <button className="primary" type="button" disabled={syncing} onClick={applySync}>
                  {syncing ? "Applying…" : "Apply sync"}
                </button>
              </div>
            )
          )}
          {syncDone && Object.keys(syncDone).length > 0 && (
            <p className="hint" style={{ color: "var(--growth)" }}>Synced: {Object.entries(syncDone).map(([id, q]) => `${yarnLabel(id)} (${q})`).join(", ")}</p>
          )}
        </div>
      )}

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
              </tr>
            </thead>
            <tbody>
              {ordersList.map((o) => (
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
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
