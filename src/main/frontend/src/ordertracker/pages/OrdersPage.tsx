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
  onCreated: (order: OrderView) => void;
}) {
  const [customerId, setCustomerId] = useState(customers[0]?.id ?? "");
  const [primaryCreatorId, setPrimaryCreatorId] = useState(creators[0]?.id ?? "");
  const [description, setDescription] = useState("");
  const [materialsCost, setMaterialsCost] = useState(0);
  const [packagingPresetId, setPackagingPresetId] = useState(presets[0]?.id ?? "");
  const [stageHours, setStageHours] = useState<Record<string, number>>({});
  const [mandatoryItems, setMandatoryItems] = useState<Record<string, string>>({});
  const [addOnsText, setAddOnsText] = useState("");
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!customerId || !primaryCreatorId) {
      setError("Pick a customer and a primary creator");
      return;
    }
    try {
      const created = await orderTrackerApi.createOrder(groupId, {
        customerId,
        primaryCreatorId,
        description,
        mandatoryItems,
        addOns: addOnsText
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean),
        packagingPresetId: packagingPresetId || null,
        stages: config.workStages.map((s) => ({
          stageKey: s.stageKey,
          assigneeCreatorId: primaryCreatorId,
          estimatedHours: stageHours[s.stageKey] ?? 0,
        })),
        materialsCost,
      });
      onCreated(created);
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

      {config.mandatoryItemTypes.length > 0 && (
        <div className="form-row">
          <label>Materials</label>
          <div className="form-grid">
            {config.mandatoryItemTypes.map((it) => (
              <div key={it.itemKey}>
                <label className="muted" style={{ fontSize: 12 }}>
                  {it.label}
                </label>
                <input
                  value={mandatoryItems[it.itemKey] ?? ""}
                  onChange={(e) => setMandatoryItems((prev) => ({ ...prev, [it.itemKey]: e.target.value }))}
                  placeholder={it.label}
                />
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="form-row">
        <label>Add-ons</label>
        <input
          value={addOnsText}
          onChange={(e) => setAddOnsText(e.target.value)}
          placeholder="Comma-separated, e.g. Gift wrap, Extra keychain"
        />
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

function ShipmentSection({
  groupId,
  order,
  onUpdated,
}: {
  groupId: string;
  order: OrderView;
  onUpdated: (o: OrderView) => void;
}) {
  const [showForm, setShowForm] = useState(false);
  const [origin, setOrigin] = useState("");
  const [destination, setDestination] = useState("");
  const [carrier, setCarrier] = useState("");
  const [tracking, setTracking] = useState("");
  const [cost, setCost] = useState(0);
  const [hours, setHours] = useState(0);

  const addLeg = async () => {
    if (!origin.trim() || !destination.trim()) return;
    const legs = order.shipmentPlan.map((l) => ({
      originLocationCode: l.originLocationCode,
      destinationLocationCode: l.destinationLocationCode,
      carrier: l.carrier,
      trackingNumber: l.trackingNumber,
      estimatedCost: l.estimatedCost,
      estimatedTimeHours: l.estimatedTimeHours,
    }));
    legs.push({
      originLocationCode: origin.trim(),
      destinationLocationCode: destination.trim(),
      carrier: carrier.trim() || null,
      trackingNumber: tracking.trim() || null,
      estimatedCost: cost,
      estimatedTimeHours: hours,
    });
    const updated = await orderTrackerApi.setShipmentPlan(groupId, order.id, legs);
    onUpdated(updated);
    setShowForm(false);
    setOrigin("");
    setDestination("");
    setCarrier("");
    setTracking("");
    setCost(0);
    setHours(0);
  };

  return (
    <div>
      <h3 style={{ fontSize: 13, textTransform: "uppercase", color: "var(--text-dim)" }}>Shipment plan</h3>
      {order.shipmentPlan.length === 0 ? (
        <p className="empty" style={{ padding: "4px 0" }}>
          No shipment legs yet.
        </p>
      ) : (
        <div className="table-wrap" style={{ marginBottom: 8 }}>
          <table>
            <thead>
              <tr>
                <th>Route</th>
                <th>Carrier</th>
                <th>Tracking</th>
                <th>Est.</th>
                <th>Shipped</th>
                <th>Delivered</th>
              </tr>
            </thead>
            <tbody>
              {order.shipmentPlan.map((leg, i) => (
                <tr key={i}>
                  <td className="mono">
                    {leg.originLocationCode} → {leg.destinationLocationCode}
                  </td>
                  <td>{leg.carrier || <span className="muted">—</span>}</td>
                  <td className="mono">{leg.trackingNumber || <span className="muted">—</span>}</td>
                  <td>
                    ₹{leg.estimatedCost} · {leg.estimatedTimeHours}h
                  </td>
                  <td>
                    {leg.shippedAt ? (
                      new Date(leg.shippedAt).toLocaleDateString()
                    ) : (
                      <button
                        type="button"
                        onClick={async () => {
                          const updated = await orderTrackerApi.markShipmentLeg(groupId, order.id, i, {
                            shippedAt: new Date().toISOString(),
                          });
                          onUpdated(updated);
                        }}
                      >
                        Mark shipped
                      </button>
                    )}
                  </td>
                  <td>
                    {leg.deliveredAt ? (
                      new Date(leg.deliveredAt).toLocaleDateString()
                    ) : leg.shippedAt ? (
                      <button
                        type="button"
                        onClick={async () => {
                          const updated = await orderTrackerApi.markShipmentLeg(groupId, order.id, i, {
                            deliveredAt: new Date().toISOString(),
                          });
                          onUpdated(updated);
                        }}
                      >
                        Mark delivered
                      </button>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showForm ? (
        <div className="toolbar" style={{ flexWrap: "wrap" }}>
          <input placeholder="Origin code" value={origin} onChange={(e) => setOrigin(e.target.value)} />
          <input placeholder="Destination code" value={destination} onChange={(e) => setDestination(e.target.value)} />
          <input placeholder="Carrier" value={carrier} onChange={(e) => setCarrier(e.target.value)} />
          <input placeholder="Tracking number" value={tracking} onChange={(e) => setTracking(e.target.value)} />
          <input type="number" placeholder="Cost" value={cost || ""} onChange={(e) => setCost(Number(e.target.value))} />
          <input
            type="number"
            placeholder="Hours"
            value={hours || ""}
            onChange={(e) => setHours(Number(e.target.value))}
          />
          <button className="primary" type="button" onClick={addLeg}>
            Add leg
          </button>
          <button type="button" onClick={() => setShowForm(false)}>
            Cancel
          </button>
        </div>
      ) : (
        <button type="button" onClick={() => setShowForm(true)}>
          + Add shipment leg
        </button>
      )}
    </div>
  );
}

function OrderRow({
  groupId,
  order,
  onUpdated,
}: {
  groupId: string;
  order: OrderView;
  onUpdated: (o: OrderView) => void;
}) {
  const [open, setOpen] = useState(false);
  const [paymentAmount, setPaymentAmount] = useState(0);

  const mandatoryEntries = Object.entries(order.mandatoryItems ?? {}).filter(([, v]) => v);

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
              {order.description && <p className="muted">{order.description}</p>}

              <h3 style={{ fontSize: 13, textTransform: "uppercase", color: "var(--text-dim)" }}>
                Materials &amp; add-ons
              </h3>
              <div className="kv" style={{ marginBottom: 12 }}>
                {mandatoryEntries.length === 0 && (!order.addOns || order.addOns.length === 0) ? (
                  <span className="muted">None recorded.</span>
                ) : (
                  <>
                    {mandatoryEntries.map(([k, v]) => (
                      <span key={k} className="chip">
                        {k}: {v}
                      </span>
                    ))}
                    {(order.addOns ?? []).map((a, i) => (
                      <span key={`addon-${i}`} className="chip">
                        + {a}
                      </span>
                    ))}
                  </>
                )}
              </div>

              <h3 style={{ fontSize: 13, textTransform: "uppercase", color: "var(--text-dim)" }}>Cost breakdown</h3>
              <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
                Materials ₹{order.materialsCost.toFixed(2)} + packaging ₹{order.packagingCost.toFixed(2)}, marked up to
                a total of <strong style={{ color: "var(--text)" }}>₹{order.totalCost.toFixed(2)}</strong>
              </p>

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
                        const updated = await orderTrackerApi.updateOrderStage(
                          groupId,
                          order.id,
                          s.stageKey,
                          Number(e.target.value) / 100
                        );
                        onUpdated(updated);
                      }}
                    />
                  </span>
                ))}
              </div>

              <h3 style={{ fontSize: 13, textTransform: "uppercase", color: "var(--text-dim)" }}>Status</h3>
              <select
                value={order.status}
                onChange={async (e) => {
                  const updated = await orderTrackerApi.updateOrderStatus(groupId, order.id, e.target.value);
                  onUpdated(updated);
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
                {order.payments.length === 0 && <span className="muted">No payments recorded.</span>}
                {order.payments.map((p, i) => (
                  <span key={i} className="chip">
                    ₹{p.amount} via {p.mode}
                  </span>
                ))}
              </div>
              <div className="toolbar" style={{ marginBottom: 16 }}>
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
                    const updated = await orderTrackerApi.addOrderPayment(groupId, order.id, {
                      amount: paymentAmount,
                      mode: "UPI",
                    });
                    setPaymentAmount(0);
                    onUpdated(updated);
                  }}
                >
                  Record payment
                </button>
              </div>

              <ShipmentSection groupId={groupId} order={order} onUpdated={onUpdated} />
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

  // Every row-level edit (stage slider, status, payment, shipment leg) patches this order
  // in place instead of re-fetching the whole list — a full reload would unmount every
  // OrderRow (including whichever one is expanded) and dump the user back at the
  // collapsed list on every single slider tick.
  const patchOrder = (updated: OrderView) => {
    setOrders((prev) => prev.map((o) => (o.id === updated.id ? updated : o)));
  };

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
          onCreated={(created) => {
            setShowForm(false);
            setOrders((prev) => [created, ...prev]);
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
              <OrderRow key={o.id} groupId={groupId} order={o} onUpdated={patchOrder} />
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
