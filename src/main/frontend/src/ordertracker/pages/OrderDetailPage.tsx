import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import type { BusinessConfig, Creator, Customer, OrderStatus, OrderView, PaymentType, PresetOption } from "../types";

const STATUSES: OrderStatus[] = ["INQUIRY", "CONFIRMED", "IN_PROGRESS", "READY_TO_SHIP", "SHIPPED", "DELIVERED", "CANCELLED"];

function EditOrderForm({
  groupId,
  order,
  config,
  presets,
  onSaved,
  onCancel,
}: {
  groupId: string;
  order: OrderView;
  config: BusinessConfig;
  presets: PresetOption[];
  onSaved: (o: OrderView) => void;
  onCancel: () => void;
}) {
  const [itemName, setItemName] = useState(order.itemName ?? "");
  const [orderReceivedDate, setOrderReceivedDate] = useState(order.orderReceivedDate?.slice(0, 10) ?? "");
  const [quotedDeliveryDate, setQuotedDeliveryDate] = useState(order.quotedDeliveryDate?.slice(0, 10) ?? "");
  const [patternType, setPatternType] = useState(order.pattern?.patternType ?? "");
  const [templateName, setTemplateName] = useState(order.pattern?.templateName ?? "");
  const [customPatternNotes, setCustomPatternNotes] = useState(order.pattern?.customPatternNotes ?? "");
  const [recipeStepsText, setRecipeStepsText] = useState(order.recipeSteps.join("\n"));
  const [mandatoryItems, setMandatoryItems] = useState<Record<string, string>>(
    Object.fromEntries(order.mandatoryItems.map((m) => [m.itemKey, m.value]))
  );
  const [craftingTimeHours, setCraftingTimeHours] = useState(order.craftingTimeHours);
  const [packagingPresetId, setPackagingPresetId] = useState(order.packaging?.tentativePresetId ?? "");
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const updated = await orderTrackerApi.updateOrder(groupId, order.id, {
        itemName,
        orderReceivedDate: orderReceivedDate ? new Date(orderReceivedDate).toISOString() : null,
        quotedDeliveryDate: quotedDeliveryDate ? new Date(quotedDeliveryDate).toISOString() : null,
        pattern: patternType
          ? { patternType, templateName: patternType === "TEMPLATE" ? templateName : null,
              customPatternNotes: patternType === "CUSTOM" ? customPatternNotes : null, attachmentUrls: [] }
          : null,
        researchItems: order.researchItems,
        recipeSteps: recipeStepsText.split("\n").map((s) => s.trim()).filter(Boolean),
        mandatoryItems: Object.entries(mandatoryItems).filter(([, v]) => v).map(([itemKey, value]) => ({
          itemKey, value, quantity: 1, unitCost: 0,
        })),
        addOns: order.addOns,
        packagingPresetId: packagingPresetId || null,
        itemizedPackaging: [],
        craftingTimeHours,
      });
      onSaved(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save");
    }
  };

  return (
    <form className="card" onSubmit={submit} style={{ marginBottom: 16 }}>
      {error && <div className="error">{error}</div>}
      <div className="form-row">
        <label>Item name</label>
        <input value={itemName} onChange={(e) => setItemName(e.target.value)} />
      </div>
      <div className="form-grid">
        <div className="form-row">
          <label>Order received</label>
          <input type="date" value={orderReceivedDate} onChange={(e) => setOrderReceivedDate(e.target.value)} />
        </div>
        <div className="form-row">
          <label>Quoted delivery</label>
          <input type="date" value={quotedDeliveryDate} onChange={(e) => setQuotedDeliveryDate(e.target.value)} />
        </div>
      </div>
      <div className="form-row">
        <label>Pattern</label>
        <select value={patternType} onChange={(e) => setPatternType(e.target.value as never)}>
          <option value="">No pattern recorded</option>
          <option value="TEMPLATE">Template</option>
          <option value="CUSTOM">Custom</option>
        </select>
      </div>
      {patternType === "TEMPLATE" && (
        <input value={templateName} onChange={(e) => setTemplateName(e.target.value)} placeholder="Template name" style={{ marginBottom: 12 }} />
      )}
      {patternType === "CUSTOM" && (
        <textarea value={customPatternNotes} onChange={(e) => setCustomPatternNotes(e.target.value)}
          placeholder="Notes kept for recreation" style={{ marginBottom: 12 }} />
      )}
      <div className="form-row">
        <label>Recipe (one step per line)</label>
        <textarea value={recipeStepsText} onChange={(e) => setRecipeStepsText(e.target.value)} />
      </div>
      <div className="form-row">
        <label>Mandatory items</label>
        <div className="form-grid">
          {config.mandatoryItemTypes.map((it) => (
            <div key={it.itemKey}>
              <label className="muted" style={{ fontSize: 12 }}>
                {it.label}
              </label>
              <input value={mandatoryItems[it.itemKey] ?? ""} onChange={(e) =>
                setMandatoryItems((prev) => ({ ...prev, [it.itemKey]: e.target.value }))} />
            </div>
          ))}
        </div>
      </div>
      <div className="form-grid">
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
        <div className="form-row">
          <label>Crafting time (hours)</label>
          <input type="number" min={0} step={0.25} value={craftingTimeHours}
            onChange={(e) => setCraftingTimeHours(Number(e.target.value))} />
        </div>
      </div>
      <button className="primary" type="submit">
        Save
      </button>
      <button type="button" onClick={onCancel} style={{ marginLeft: 8 }}>
        Cancel
      </button>
    </form>
  );
}

export default function OrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const [order, setOrder] = useState<OrderView | null>(null);
  const [config, setConfig] = useState<BusinessConfig | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [creators, setCreators] = useState<Creator[]>([]);
  const [presets, setPresets] = useState<PresetOption[]>([]);
  const [editing, setEditing] = useState(false);
  const [paymentAmount, setPaymentAmount] = useState(0);
  const [paymentType, setPaymentType] = useState<PaymentType>("ADVANCE");

  const load = () => {
    Promise.all([
      orderTrackerApi.order(groupId, orderId!),
      orderTrackerApi.businessConfig(groupId),
      orderTrackerApi.customers(groupId),
      orderTrackerApi.creators(groupId),
      orderTrackerApi.packagingPresets(groupId),
    ]).then(([o, cfg, c, cr, p]) => {
      setOrder(o);
      setConfig(cfg);
      setCustomers(c);
      setCreators(cr);
      setPresets(p);
    });
  };

  useEffect(load, [groupId, orderId]);

  if (!order || !config) return <p className="muted">Loading…</p>;

  const customer = customers.find((c) => c.id === order.customerId);
  const creatorName = (id: string | null) => (id ? creators.find((c) => c.id === id)?.name ?? id : "—");
  const dueDate = order.orderType === "INDIVIDUAL" ? order.costEstimate?.computedDueDate : order.bulkDetails?.computedDueDate;
  const finalCost = order.orderType === "INDIVIDUAL" ? order.costEstimate?.finalCost ?? 0 : order.bulkDetails?.totalFinalCost ?? 0;

  return (
    <div>
      <div className="toolbar" style={{ marginBottom: 4 }}>
        <span className="mono" style={{ fontSize: 20, fontWeight: 700 }}>
          {order.orderNumber}
        </span>
        <span className="badge">{order.orderType}</span>
        <select
          value={order.status}
          onChange={async (e) => setOrder(await orderTrackerApi.updateStatus(groupId, order.id, e.target.value))}
        >
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s.replace(/_/g, " ")}
            </option>
          ))}
        </select>
        <span className="badge">{order.paymentStatus}</span>
        <span className="spacer" />
        {!editing && order.orderType === "INDIVIDUAL" && (
          <button onClick={() => setEditing(true)}>Edit order</button>
        )}
      </div>
      <p className="page-sub">{order.itemName}</p>

      {editing && order.orderType === "INDIVIDUAL" && (
        <EditOrderForm
          groupId={groupId}
          order={order}
          config={config}
          presets={presets}
          onSaved={(o) => {
            setOrder(o);
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
        />
      )}

      <div className="grid">
        <div className="card">
          <h2>Customer</h2>
          <div className="row"><span className="k">Name</span><span className="v">{customer?.name ?? "—"}</span></div>
          <div className="row"><span className="k">Channel</span><span className="v">{customer?.acquisitionChannel ?? "—"}</span></div>
          <div className="row"><span className="k">Logged by</span><span className="v">{creatorName(order.createdByCreatorId)}</span></div>
        </div>

        <div className="card">
          <h2>Pattern</h2>
          {order.pattern ? (
            <>
              <div className="row"><span className="k">Type</span><span className="v">{order.pattern.patternType}</span></div>
              {order.pattern.templateName && (
                <div className="row"><span className="k">Template</span><span className="v">{order.pattern.templateName}</span></div>
              )}
              {order.pattern.customPatternNotes && (
                <div className="row"><span className="k">Notes</span><span className="v">{order.pattern.customPatternNotes}</span></div>
              )}
            </>
          ) : (
            <p className="empty">Not recorded.</p>
          )}
        </div>

        <div className="card">
          <h2>Recipe</h2>
          {order.recipeSteps.length === 0 ? (
            <p className="empty">No steps recorded.</p>
          ) : (
            <ol style={{ margin: 0, paddingLeft: 18 }}>
              {order.recipeSteps.map((s, i) => (
                <li key={i}>{s}</li>
              ))}
            </ol>
          )}
        </div>
      </div>

      {order.orderType === "INDIVIDUAL" ? (
        <div className="grid">
          <div className="card">
            <h2>Mandatory items</h2>
            {order.mandatoryItems.length === 0 ? (
              <p className="empty">None recorded.</p>
            ) : (
              order.mandatoryItems.map((m) => (
                <div className="row" key={m.itemKey}>
                  <span className="k">{m.itemKey}</span>
                  <span className="v">{m.value}</span>
                </div>
              ))
            )}
          </div>
          <div className="card">
            <h2>Add-ons</h2>
            {order.addOns.length === 0 ? (
              <p className="empty">None.</p>
            ) : (
              <div className="kv">
                {order.addOns.map((a, i) => (
                  <span key={i} className="chip">
                    {a.name} × {a.quantity}
                  </span>
                ))}
              </div>
            )}
          </div>
          <div className="card">
            <h2>Packaging</h2>
            <div className="row"><span className="k">Cost</span><span className="v">₹{order.packaging?.cost.toFixed(2) ?? "0.00"}</span></div>
            <div className="row"><span className="k">Time</span><span className="v">{order.packaging?.timeHours ?? 0}h</span></div>
          </div>
        </div>
      ) : (
        <>
          <h2 className="settings-section">Variants</h2>
          {order.bulkDetails?.variants.map((v) => (
            <div className="variant-block card" key={v.variantId} style={{ marginBottom: 12 }}>
              <div className="toolbar">
                <strong>{v.label}</strong>
                <span className="muted">Qty {v.quantity}</span>
                <span className="spacer" />
                <span>₹{v.perUnitCost.toFixed(2)}/unit</span>
                <span>Total ₹{v.totalCost.toFixed(2)}</span>
              </div>
              <div className="grid">
                <div className="card" style={{ background: "var(--bg-elev-2)" }}>
                  <h2>Mandatory items</h2>
                  {v.mandatoryItems.map((m) => (
                    <div className="row" key={m.itemKey}><span className="k">{m.itemKey}</span><span className="v">{m.value}</span></div>
                  ))}
                </div>
                <div className="card" style={{ background: "var(--bg-elev-2)" }}>
                  <h2>Split across creators</h2>
                  {v.splitAllocation.map((s) => (
                    <div className="row" key={s.creatorId}>
                      <span className="k">{creatorName(s.creatorId)}</span>
                      <span className="v">{s.quantityAssigned}</span>
                    </div>
                  ))}
                </div>
                <div className="card" style={{ background: "var(--bg-elev-2)" }}>
                  <h2>Crocheting progress</h2>
                  {v.splitAllocation.map((s) => {
                    const entry = s.stageProgress.find((sp) => sp.stageKey === "crocheting");
                    return (
                      <div key={s.creatorId} className="row">
                        <span className="k">{creatorName(s.creatorId)}</span>
                        <input
                          type="number"
                          min={0}
                          max={s.quantityAssigned}
                          style={{ width: 70 }}
                          value={entry?.unitsCompleted ?? 0}
                          onChange={async (e) =>
                            setOrder(
                              await orderTrackerApi.updateBulkSplitProgress(
                                groupId, order.id, v.variantId, s.creatorId, "crocheting", Number(e.target.value)
                              )
                            )
                          }
                        />
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          ))}

          <div className="card" style={{ marginBottom: 16 }}>
            <h2>Batch-tracked stages</h2>
            {order.bulkDetails?.stageProgress.map((sp) => (
              <div className="row" key={sp.stageKey}>
                <span className="k">{sp.stageKey}</span>
                <input
                  type="number"
                  min={0}
                  max={sp.totalUnits}
                  style={{ width: 90 }}
                  value={sp.unitsCompleted}
                  onChange={async (e) =>
                    setOrder(await orderTrackerApi.updateBulkStageProgress(groupId, order.id, sp.stageKey, Number(e.target.value)))
                  }
                />
                <span className="muted">/ {sp.totalUnits}</span>
              </div>
            ))}
          </div>
        </>
      )}

      {order.orderType === "INDIVIDUAL" && (
        <div className="card" style={{ marginBottom: 16 }}>
          <h2>Work stages</h2>
          {order.stageAssignments.map((s) => (
            <div className="row" key={s.stageKey}>
              <span className="k">{s.stageKey} — {creatorName(s.assignedCreatorId)}</span>
              <label style={{ display: "flex", gap: 6, alignItems: "center" }}>
                <input
                  type="checkbox"
                  checked={s.unitsCompleted >= 1}
                  onChange={async (e) =>
                    setOrder(
                      await orderTrackerApi.updateStageAssignment(
                        groupId, order.id, s.stageKey, s.assignedCreatorId, e.target.checked ? 1 : 0
                      )
                    )
                  }
                />
                Done
              </label>
            </div>
          ))}
          <p className="muted" style={{ fontSize: 13, marginTop: 8 }}>{order.completionPercentage.toFixed(0)}% complete</p>
        </div>
      )}

      <div className="grid cols-2">
        <div className="card" style={{ background: "var(--bg-elev-2)" }}>
          <h2>Cost &amp; time</h2>
          {order.orderType === "INDIVIDUAL" && order.costEstimate ? (
            <>
              {order.costEstimate.itemizedBreakdown.map((b) => (
                <div className="row" key={b.label}><span className="k">{b.label}</span><span className="v">₹{b.amount.toFixed(2)}</span></div>
              ))}
              <div className="row" style={{ fontWeight: 700 }}>
                <span className="k">Final price</span><span className="v">₹{order.costEstimate.finalCost.toFixed(2)}</span>
              </div>
              <div className="row"><span className="k">Crafting time</span><span className="v">{order.costEstimate.grossTimeHours}h</span></div>
            </>
          ) : (
            <>
              <div className="row"><span className="k">Total quantity</span><span className="v">{order.bulkDetails?.totalQuantity}</span></div>
              <div className="row" style={{ fontWeight: 700 }}>
                <span className="k">Total cost</span><span className="v">₹{order.bulkDetails?.totalFinalCost.toFixed(2)}</span>
              </div>
              <div className="row"><span className="k">Total time</span><span className="v">{order.bulkDetails?.totalTimeHours}h</span></div>
            </>
          )}
          <div className="row"><span className="k">Promised delivery</span>
            <span className="v">{dueDate ? new Date(dueDate).toLocaleDateString() : "—"}</span></div>
        </div>

        <div className="card">
          <h2>Payment</h2>
          {order.payments.length === 0 ? (
            <p className="empty">No payments yet.</p>
          ) : (
            order.payments.map((p) => (
              <div className="row" key={p.paymentId}>
                <span className="k">{p.type}</span>
                <span className="v">₹{p.amount.toFixed(2)} ({p.mode})</span>
              </div>
            ))
          )}
          <div className="row"><span className="k">Balance</span><span className="v">₹{order.balanceAmount.toFixed(2)}</span></div>
          <div className="toolbar" style={{ marginTop: 10 }}>
            <select value={paymentType} onChange={(e) => setPaymentType(e.target.value as PaymentType)}>
              <option value="ADVANCE">Advance</option>
              <option value="INSTALLMENT">Installment</option>
              <option value="FINAL">Final</option>
              <option value="REFUND">Refund</option>
            </select>
            <input type="number" placeholder="Amount" value={paymentAmount || ""} onChange={(e) => setPaymentAmount(Number(e.target.value))} />
            <button
              className="primary"
              onClick={async () => {
                if (!paymentAmount) return;
                setOrder(await orderTrackerApi.addPayment(groupId, order.id, { type: paymentType, amount: paymentAmount, mode: "UPI" }));
                setPaymentAmount(0);
              }}
            >
              Record
            </button>
          </div>
        </div>
      </div>

      <ShippingCard groupId={groupId} order={order} onUpdated={setOrder} />
    </div>
  );
}

function ShippingCard({ groupId, order, onUpdated }: { groupId: string; order: OrderView; onUpdated: (o: OrderView) => void }) {
  const [showForm, setShowForm] = useState(false);
  const [origin, setOrigin] = useState("");
  const [destination, setDestination] = useState("");
  const [carrier, setCarrier] = useState("");
  const [tracking, setTracking] = useState("");

  const addStop = async () => {
    if (!origin.trim() || !destination.trim()) return;
    const stops = order.shipmentPlan.map((s) => ({
      stopOrder: s.stopOrder, type: s.type, originLocationCode: s.originLocationCode,
      destinationLocationCode: s.destinationLocationCode, laneId: s.laneId, estimatedCost: s.estimatedCost,
      estimatedTimeHours: s.estimatedTimeHours, carrier: s.carrier, trackingNumber: s.trackingNumber,
      triggerDate: s.triggerDate,
    }));
    stops.push({
      stopOrder: stops.length + 1, type: "FINAL_DELIVERY", originLocationCode: origin, destinationLocationCode: destination,
      laneId: null, estimatedCost: 0, estimatedTimeHours: 0, carrier: carrier || null, trackingNumber: tracking || null,
      triggerDate: null,
    });
    onUpdated(await orderTrackerApi.setShipmentPlan(groupId, order.id, stops));
    setShowForm(false);
    setOrigin("");
    setDestination("");
    setCarrier("");
    setTracking("");
  };

  return (
    <div className="card" style={{ marginTop: 16 }}>
      <h2>Shipping</h2>
      {order.shipmentPlan.length === 0 ? (
        <p className="empty">No shipment stops yet.</p>
      ) : (
        order.shipmentPlan.map((s, i) => (
          <div className="row" key={i}>
            <span className="k">
              {s.type} — {s.originLocationCode} → {s.destinationLocationCode}
              {s.carrier ? ` (${s.carrier})` : ""}
            </span>
            <span className="v">
              {s.deliveredConfirmed ? "Delivered" : s.shippedDate ? "Shipped" : (
                <button type="button" onClick={async () =>
                  onUpdated(await orderTrackerApi.markShipmentStop(groupId, order.id, i, { shippedDate: new Date().toISOString() }))}>
                  Mark shipped
                </button>
              )}
              {!s.deliveredConfirmed && s.shippedDate && (
                <button type="button" style={{ marginLeft: 6 }} onClick={async () =>
                  onUpdated(await orderTrackerApi.markShipmentStop(groupId, order.id, i, { deliveredConfirmed: true }))}>
                  Mark delivered
                </button>
              )}
            </span>
          </div>
        ))
      )}
      {showForm ? (
        <div className="toolbar" style={{ marginTop: 10, flexWrap: "wrap" }}>
          <input placeholder="Origin code" value={origin} onChange={(e) => setOrigin(e.target.value)} />
          <input placeholder="Destination code" value={destination} onChange={(e) => setDestination(e.target.value)} />
          <input placeholder="Carrier" value={carrier} onChange={(e) => setCarrier(e.target.value)} />
          <input placeholder="Tracking number" value={tracking} onChange={(e) => setTracking(e.target.value)} />
          <button className="primary" type="button" onClick={addStop}>
            Add stop
          </button>
          <button type="button" onClick={() => setShowForm(false)}>
            Cancel
          </button>
        </div>
      ) : (
        <button type="button" style={{ marginTop: 10 }} onClick={() => setShowForm(true)}>
          + Add shipment stop
        </button>
      )}
    </div>
  );
}
