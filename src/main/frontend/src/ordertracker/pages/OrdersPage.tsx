import { useEffect, useState } from "react";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import type { BusinessConfig, Creator, Customer, OrderStatus, OrderView, PresetOption } from "../types";

const STATUSES: OrderStatus[] = ["RECEIVED", "IN_PROGRESS", "READY_FOR_SHIPMENT", "SHIPPED", "DELIVERED", "CANCELLED"];

function NewOrderForm({
  groupId,
  config,
  customers,
  creators,
  presets,
  onCreated,
}: {
  groupId: string;
  config: BusinessConfig;
  customers: Customer[];
  creators: Creator[];
  presets: PresetOption[];
  onCreated: () => void;
}) {
  const [customerId, setCustomerId] = useState(customers[0]?.id ?? "");
  const [primaryCreatorId, setPrimaryCreatorId] = useState(creators[0]?.id ?? "");
  const [description, setDescription] = useState("");
  const [materialsCost, setMaterialsCost] = useState(0);
  const [packagingPresetId, setPackagingPresetId] = useState(presets[0]?.id ?? "");
  const [stageHours, setStageHours] = useState<Record<string, number>>({});
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!customerId || !primaryCreatorId) {
      setError("Pick a customer and a primary creator");
      return;
    }
    try {
      await orderTrackerApi.createOrder(groupId, {
        customerId,
        primaryCreatorId,
        description,
        mandatoryItems: {},
        addOns: [],
        packagingPresetId: packagingPresetId || null,
        stages: config.workStages.map((s) => ({
          stageKey: s.stageKey,
          assigneeCreatorId: primaryCreatorId,
          estimatedHours: stageHours[s.stageKey] ?? 0,
        })),
        materialsCost,
      });
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create order");
    }
  };

  return (
    <form className="card" onSubmit={submit} style={{ marginBottom: 16 }}>
      {error && <div className="error">{error}</div>}
      <div className="form-grid">
        <div className="form-row">
          <label>Customer</label>
          <select value={customerId} onChange={(e) => setCustomerId(e.target.value)} required>
            <option value="" disabled>
              Select a customer
            </option>
            {customers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <label>Primary creator</label>
          <select value={primaryCreatorId} onChange={(e) => setPrimaryCreatorId(e.target.value)} required>
            <option value="" disabled>
              Select a creator
            </option>
            {creators.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.hoursAvailablePerDay}h/day)
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="form-row">
        <label>Description</label>
        <input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="e.g. Amigurumi bear" />
      </div>
      <div className="form-row">
        <label>Estimated hours per stage</label>
        <div className="form-grid">
          {config.workStages.map((s) => (
            <div key={s.stageKey}>
              <label className="muted" style={{ fontSize: 12 }}>
                {s.label}
              </label>
              <input
                type="number"
                min={0}
                step={0.25}
                value={stageHours[s.stageKey] ?? 0}
                onChange={(e) => setStageHours((prev) => ({ ...prev, [s.stageKey]: Number(e.target.value) }))}
              />
            </div>
          ))}
        </div>
      </div>
      <div className="form-grid">
        <div className="form-row">
          <label>Materials cost</label>
          <input type="number" min={0} value={materialsCost} onChange={(e) => setMaterialsCost(Number(e.target.value))} />
        </div>
        <div className="form-row">
          <label>Packaging preset</label>
          <select value={packagingPresetId} onChange={(e) => setPackagingPresetId(e.target.value)}>
            <option value="">None</option>
            {presets.map((p) => (
              <option key={p.id} value={p.id}>
                {p.label}
              </option>
            ))}
          </select>
        </div>
      </div>
      <button className="primary" type="submit">
        Create order
      </button>
    </form>
  );
}

function OrderRow({ groupId, order, onChanged }: { groupId: string; order: OrderView; onChanged: () => void }) {
  const [open, setOpen] = useState(false);
  const [paymentAmount, setPaymentAmount] = useState(0);

  return (
    <tr>
      <td colSpan={7} style={{ padding: 0 }}>
        <div className="card" style={{ margin: "8px 0" }}>
          <div className="toolbar" onClick={() => setOpen((o) => !o)} style={{ cursor: "pointer" }}>
            <strong className="mono">{order.orderNumber}</strong>
            <span className="badge">{order.status}</span>
            <span className="badge">{order.paymentStatus}</span>
            <span>{order.overallCompletionPercent.toFixed(0)}% done</span>
            <span className="spacer" />
            <span>Due {new Date(order.computedDueDate).toLocaleDateString()}</span>
            <span>Total ₹{order.totalCost.toFixed(2)}</span>
          </div>

          {open && (
            <div>
              <p className="muted">{order.description}</p>

              <h3 style={{ fontSize: 13, textTransform: "uppercase", color: "var(--text-dim)" }}>Stages</h3>
              <div className="kv" style={{ marginBottom: 12 }}>
                {order.stageProgress.map((s) => (
                  <span key={s.stageKey} className="chip">
                    {s.label}: {(s.completionFraction * 100).toFixed(0)}%
                    <input
                      type="range"
                      min={0}
                      max={100}
                      value={s.completionFraction * 100}
                      onChange={async (e) => {
                        await orderTrackerApi.updateOrderStage(groupId, order.id, s.stageKey, Number(e.target.value) / 100);
                        onChanged();
                      }}
                    />
                  </span>
                ))}
              </div>

              <h3 style={{ fontSize: 13, textTransform: "uppercase", color: "var(--text-dim)" }}>Status</h3>
              <select
                value={order.status}
                onChange={async (e) => {
                  await orderTrackerApi.updateOrderStatus(groupId, order.id, e.target.value);
                  onChanged();
                }}
                style={{ marginBottom: 12 }}
              >
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s.replace(/_/g, " ")}
                  </option>
                ))}
              </select>

              <h3 style={{ fontSize: 13, textTransform: "uppercase", color: "var(--text-dim)" }}>Payments</h3>
              <div className="kv" style={{ marginBottom: 8 }}>
                {order.payments.map((p, i) => (
                  <span key={i} className="chip">
                    ₹{p.amount} via {p.mode}
                  </span>
                ))}
              </div>
              <div className="toolbar">
                <input
                  type="number"
                  placeholder="Amount"
                  value={paymentAmount || ""}
                  onChange={(e) => setPaymentAmount(Number(e.target.value))}
                />
                <button
                  className="primary"
                  onClick={async () => {
                    if (!paymentAmount) return;
                    await orderTrackerApi.addOrderPayment(groupId, order.id, { amount: paymentAmount, mode: "UPI" });
                    setPaymentAmount(0);
                    onChanged();
                  }}
                >
                  Record payment
                </button>
              </div>
            </div>
          )}
        </div>
      </td>
    </tr>
  );
}

export default function OrdersPage() {
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const [orders, setOrders] = useState<OrderView[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [creators, setCreators] = useState<Creator[]>([]);
  const [presets, setPresets] = useState<PresetOption[]>([]);
  const [config, setConfig] = useState<BusinessConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const [o, c, cr, p, cfg] = await Promise.all([
        orderTrackerApi.orders(groupId),
        orderTrackerApi.customers(groupId),
        orderTrackerApi.creators(groupId),
        orderTrackerApi.packagingPresets(groupId),
        orderTrackerApi.businessConfig(groupId),
      ]);
      setOrders(o);
      setCustomers(c);
      setCreators(cr);
      setPresets(p);
      setConfig(cfg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId]);

  return (
    <div>
      <div className="toolbar">
        <h1 className="page-title" style={{ marginBottom: 0 }}>
          Orders
        </h1>
        <span className="spacer" />
        <button className="primary" onClick={() => setShowForm((s) => !s)} disabled={!config || creators.length === 0}>
          {showForm ? "Close" : "+ New order"}
        </button>
      </div>

      {creators.length === 0 && (
        <p className="empty">Set up at least one creator profile under Business settings before creating orders.</p>
      )}
      {customers.length === 0 && <p className="empty">Add a customer first.</p>}

      {showForm && config && (
        <NewOrderForm
          groupId={groupId}
          config={config}
          customers={customers}
          creators={creators}
          presets={presets}
          onCreated={() => {
            setShowForm(false);
            void load();
          }}
        />
      )}

      {loading ? (
        <p className="muted">Loading…</p>
      ) : orders.length === 0 ? (
        <p className="empty">No orders yet.</p>
      ) : (
        <table>
          <tbody>
            {orders.map((o) => (
              <OrderRow key={o.id} groupId={groupId} order={o} onChanged={load} />
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
