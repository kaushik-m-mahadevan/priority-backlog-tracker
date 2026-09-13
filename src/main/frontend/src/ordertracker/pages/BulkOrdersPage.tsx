import { useEffect, useState } from "react";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import type { BulkOrderView, Creator, Customer } from "../types";

interface VariantDraft {
  label: string;
  quantity: number;
}

interface SplitDraft {
  creatorId: string;
  assignedQuantity: number;
  estimatedHoursPerUnit: number;
}

export default function BulkOrdersPage() {
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const [orders, setOrders] = useState<BulkOrderView[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [creators, setCreators] = useState<Creator[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [customerId, setCustomerId] = useState("");
  const [description, setDescription] = useState("");
  const [materialsCost, setMaterialsCost] = useState(0);
  const [variants, setVariants] = useState<VariantDraft[]>([{ label: "", quantity: 1 }]);
  const [splits, setSplits] = useState<SplitDraft[]>([{ creatorId: "", assignedQuantity: 1, estimatedHoursPerUnit: 1 }]);

  const load = async () => {
    setLoading(true);
    try {
      const [o, c, cr] = await Promise.all([
        orderTrackerApi.bulkOrders(groupId),
        orderTrackerApi.customers(groupId),
        orderTrackerApi.creators(groupId),
      ]);
      setOrders(o);
      setCustomers(c);
      setCreators(cr);
      if (!customerId) setCustomerId(c[0]?.id ?? "");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await orderTrackerApi.createBulkOrder(groupId, {
        customerId,
        description,
        variants: variants.filter((v) => v.label.trim()),
        creatorSplits: splits.filter((s) => s.creatorId),
        materialsCost,
      });
      setShowForm(false);
      setVariants([{ label: "", quantity: 1 }]);
      setSplits([{ creatorId: "", assignedQuantity: 1, estimatedHoursPerUnit: 1 }]);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create bulk order");
    }
  };

  return (
    <div>
      <div className="toolbar">
        <h1 className="page-title" style={{ marginBottom: 0 }}>
          Bulk Orders
        </h1>
        <span className="spacer" />
        <button className="primary" onClick={() => setShowForm((s) => !s)} disabled={creators.length === 0}>
          {showForm ? "Close" : "+ New bulk order"}
        </button>
      </div>

      {showForm && (
        <form className="card" onSubmit={submit} style={{ marginBottom: 16 }}>
          {error && <div className="error">{error}</div>}
          <div className="form-row">
            <label>Customer</label>
            <select value={customerId} onChange={(e) => setCustomerId(e.target.value)} required>
              {customers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          <div className="form-row">
            <label>Description</label>
            <input value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>

          <div className="form-row">
            <label>Variants</label>
            {variants.map((v, i) => (
              <div className="toolbar" key={i}>
                <input
                  placeholder="Label (e.g. Small / cream)"
                  value={v.label}
                  onChange={(e) =>
                    setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))
                  }
                />
                <input
                  type="number"
                  min={1}
                  placeholder="Qty"
                  value={v.quantity}
                  onChange={(e) =>
                    setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, quantity: Number(e.target.value) } : x)))
                  }
                />
              </div>
            ))}
            <button type="button" onClick={() => setVariants((prev) => [...prev, { label: "", quantity: 1 }])}>
              + Add variant
            </button>
          </div>

          <div className="form-row">
            <label>Creator splits</label>
            {splits.map((s, i) => (
              <div className="toolbar" key={i}>
                <select
                  value={s.creatorId}
                  onChange={(e) =>
                    setSplits((prev) => prev.map((x, j) => (j === i ? { ...x, creatorId: e.target.value } : x)))
                  }
                >
                  <option value="">Select creator</option>
                  {creators.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.hoursAvailablePerDay}h/day)
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  min={1}
                  placeholder="Qty assigned"
                  value={s.assignedQuantity}
                  onChange={(e) =>
                    setSplits((prev) =>
                      prev.map((x, j) => (j === i ? { ...x, assignedQuantity: Number(e.target.value) } : x))
                    )
                  }
                />
                <input
                  type="number"
                  min={0}
                  step={0.25}
                  placeholder="Hours/unit"
                  value={s.estimatedHoursPerUnit}
                  onChange={(e) =>
                    setSplits((prev) =>
                      prev.map((x, j) => (j === i ? { ...x, estimatedHoursPerUnit: Number(e.target.value) } : x))
                    )
                  }
                />
              </div>
            ))}
            <button
              type="button"
              onClick={() => setSplits((prev) => [...prev, { creatorId: "", assignedQuantity: 1, estimatedHoursPerUnit: 1 }])}
            >
              + Add creator split
            </button>
          </div>

          <div className="form-row">
            <label>Materials cost (total)</label>
            <input type="number" min={0} value={materialsCost} onChange={(e) => setMaterialsCost(Number(e.target.value))} />
          </div>

          <button className="primary" type="submit">
            Create bulk order
          </button>
        </form>
      )}

      {loading ? (
        <p className="muted">Loading…</p>
      ) : orders.length === 0 ? (
        <p className="empty">No bulk orders yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Order #</th>
                <th>Qty</th>
                <th>Status</th>
                <th>Completion</th>
                <th>Due</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td className="mono">{o.orderNumber}</td>
                  <td>{o.totalQuantity}</td>
                  <td>
                    <span className="badge">{o.status}</span>
                  </td>
                  <td>{o.overallCompletionPercent.toFixed(0)}%</td>
                  <td>{new Date(o.computedDueDate).toLocaleDateString()}</td>
                  <td>₹{o.totalCost.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
