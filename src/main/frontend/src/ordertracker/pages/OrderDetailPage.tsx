import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import {
  AddOnsFields,
  MandatoryItemsFields,
  duplicateVariant,
  validateSplits,
  type LineItemDraft,
  type MandatoryItemDraft,
  type VariantDraft,
} from "../OrderFormFields";
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
  const [mandatoryItems, setMandatoryItems] = useState<MandatoryItemDraft[]>(
    order.mandatoryItems.length > 0
      ? order.mandatoryItems.map((m) => ({ itemKey: m.itemKey, value: m.value, quantity: m.quantity, unitCost: m.unitCost }))
      : config.mandatoryItemTypes.map((it) => ({ itemKey: it.itemKey, value: "", quantity: 1, unitCost: 0 }))
  );
  const [addOns, setAddOns] = useState<LineItemDraft[]>(
    order.addOns.map((a) => ({ name: a.name, quantity: a.quantity, unitCost: a.unitCost, unitTimeHours: a.unitTimeHours ?? 0 }))
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
        mandatoryItems: mandatoryItems.filter((m) => m.value.trim()),
        addOns: addOns.filter((a) => a.name.trim()),
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

      <label className="muted" style={{ fontSize: 12, display: "block", marginBottom: 6 }}>
        Mandatory items
      </label>
      <MandatoryItemsFields items={mandatoryItems} onChange={setMandatoryItems} />

      <label className="muted" style={{ fontSize: 12, display: "block", margin: "12px 0 6px" }}>
        Add-ons
      </label>
      <AddOnsFields addOns={addOns} onChange={setAddOns} />

      <div className="form-grid" style={{ marginTop: 12 }}>
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

function EditBulkDetailsForm({
  groupId,
  order,
  creators,
  onSaved,
  onCancel,
}: {
  groupId: string;
  order: OrderView;
  creators: Creator[];
  onSaved: (o: OrderView) => void;
  onCancel: () => void;
}) {
  const [variants, setVariants] = useState<VariantDraft[]>(
    (order.bulkDetails?.variants ?? []).map((v) => ({
      variantId: v.variantId,
      label: v.label,
      quantity: v.quantity,
      mandatoryItems: v.mandatoryItems.map((m) => ({ itemKey: m.itemKey, value: m.value, quantity: m.quantity, unitCost: m.unitCost })),
      addOns: v.addOns.map((a) => ({ name: a.name, quantity: a.quantity, unitCost: a.unitCost, unitTimeHours: a.unitTimeHours ?? 0 })),
      craftingTimeHours: v.craftingTimeHours,
      splitAllocation: v.splitAllocation.map((s) => ({ creatorId: s.creatorId, quantityAssigned: s.quantityAssigned })),
    }))
  );
  const [coordinatingCreatorId, setCoordinatingCreatorId] = useState(order.bulkDetails?.coordinatingCreatorId ?? "");
  const [logisticsBufferDays, setLogisticsBufferDays] = useState(order.bulkDetails?.logisticsBufferDays ?? 0);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const splitError = validateSplits(variants);
    if (splitError) {
      setError(splitError);
      return;
    }
    try {
      const updated = await orderTrackerApi.updateBulkDetails(groupId, order.id, {
        variants: variants
          .filter((v) => v.label.trim())
          .map((v) => ({
            variantId: v.variantId ?? null,
            label: v.label,
            quantity: v.quantity,
            mandatoryItems: v.mandatoryItems.filter((m) => m.value.trim()),
            addOns: v.addOns.filter((a) => a.name.trim()),
            craftingTimeHours: v.craftingTimeHours,
            splitAllocation: v.splitAllocation.filter((s) => s.creatorId),
          })),
        coordinatingCreatorId: coordinatingCreatorId || null,
        logisticsBufferDays,
      });
      onSaved(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save");
    }
  };

  return (
    <form className="card" onSubmit={submit} style={{ marginBottom: 16 }}>
      {error && <div className="error">{error}</div>}

      <div className="form-grid">
        <div className="form-row">
          <label>Coordinating creator</label>
          <select value={coordinatingCreatorId} onChange={(e) => setCoordinatingCreatorId(e.target.value)}>
            <option value="">None</option>
            {creators.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <label>Logistics buffer (days)</label>
          <input type="number" min={0} value={logisticsBufferDays} onChange={(e) => setLogisticsBufferDays(Number(e.target.value))} />
        </div>
      </div>

      {variants.map((v, i) => {
        const assigned = v.splitAllocation.filter((s) => s.creatorId).reduce((sum, s) => sum + s.quantityAssigned, 0);
        const mismatch = v.splitAllocation.length > 0 && assigned !== v.quantity;
        return (
          <div className="card" key={i} style={{ marginBottom: 16, background: "var(--bg-elev-2)" }}>
            <div className="form-grid">
              <div className="form-row">
                <label>Label</label>
                <input value={v.label} onChange={(e) =>
                  setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))} />
              </div>
              <div className="form-row">
                <label>Quantity</label>
                <input type="number" min={1} value={v.quantity} onChange={(e) =>
                  setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, quantity: Number(e.target.value) } : x)))} />
              </div>
            </div>

            <label className="muted" style={{ fontSize: 12 }}>
              Mandatory items (per unit)
            </label>
            <MandatoryItemsFields
              items={v.mandatoryItems}
              onChange={(items) => setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, mandatoryItems: items } : x)))}
            />

            <label className="muted" style={{ fontSize: 12, marginTop: 12, display: "block" }}>
              Add-ons (per unit)
            </label>
            <AddOnsFields
              addOns={v.addOns}
              onChange={(a) => setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, addOns: a } : x)))}
            />

            <div className="form-row" style={{ marginTop: 12, maxWidth: 220 }}>
              <label className="muted" style={{ fontSize: 12 }}>
                Crafting time/unit (hours)
              </label>
              <input type="number" min={0} step={0.1} value={v.craftingTimeHours} onChange={(e) =>
                setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, craftingTimeHours: Number(e.target.value) } : x)))} />
            </div>

            <label className="muted" style={{ fontSize: 12, marginTop: 12, display: "block" }}>
              Split across creators — must add up to the quantity above ({v.quantity})
            </label>
            {v.splitAllocation.map((s, k) => (
              <div className="toolbar" key={k}>
                <select value={s.creatorId} onChange={(e) =>
                  setVariants((prev) => prev.map((x, j) => j === i ? {
                    ...x, splitAllocation: x.splitAllocation.map((sp, l) => l === k ? { ...sp, creatorId: e.target.value } : sp)
                  } : x))}>
                  <option value="">Select creator</option>
                  {creators.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
                <input type="number" min={1} placeholder="Qty" value={s.quantityAssigned} onChange={(e) =>
                  setVariants((prev) => prev.map((x, j) => j === i ? {
                    ...x, splitAllocation: x.splitAllocation.map((sp, l) => l === k ? { ...sp, quantityAssigned: Number(e.target.value) } : sp)
                  } : x))} />
                <button type="button" onClick={() =>
                  setVariants((prev) => prev.map((x, j) => j === i
                    ? { ...x, splitAllocation: x.splitAllocation.filter((_, l) => l !== k) } : x))}>
                  Remove
                </button>
              </div>
            ))}
            <button type="button" onClick={() =>
              setVariants((prev) => prev.map((x, j) => j === i
                ? { ...x, splitAllocation: [...x.splitAllocation, { creatorId: "", quantityAssigned: 1 }] } : x))}>
              + Add creator split
            </button>
            <p className={mismatch ? "hint bad" : "hint"} style={{ marginTop: 6 }}>
              Assigned so far: {assigned} / {v.quantity}
            </p>
          </div>
        );
      })}
      <button type="button" onClick={() => setVariants((prev) => [...prev, duplicateVariant(prev[prev.length - 1])])}>
        + Add variant (copies the last one)
      </button>

      <div style={{ marginTop: 16 }}>
        <button className="primary" type="submit">
          Save
        </button>
        <button type="button" onClick={onCancel} style={{ marginLeft: 8 }}>
          Cancel
        </button>
      </div>
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
  const creatorName = (id: string | null | undefined) => {
    if (!id) return <span className="muted">Unassigned</span>;
    return creators.find((c) => c.id === id)?.name ?? <span className="muted">Unknown creator</span>;
  };
  const dueDate = order.orderType === "INDIVIDUAL" ? order.costEstimate?.computedDueDate : order.bulkDetails?.computedDueDate;
  const splitTrackedStageKeys = config.workStages.filter((s) => s.splitTracked).map((s) => s.stageKey);

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
        {!editing && (
          <button onClick={() => setEditing(true)}>Edit order</button>
        )}
      </div>
      <p className="page-sub">{order.itemName}</p>

      <div className="toolbar" style={{ marginBottom: 20 }}>
        <div className="order-progress-track">
          <div className="order-progress-fill" style={{ width: `${Math.min(100, order.completionPercentage)}%` }} />
        </div>
        <span className="mono" style={{ fontSize: 13, minWidth: 44, textAlign: "right" }}>
          {order.completionPercentage.toFixed(0)}%
        </span>
      </div>

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
      {editing && order.orderType === "BULK" && (
        <EditBulkDetailsForm
          groupId={groupId}
          order={order}
          creators={creators}
          onSaved={(o) => {
            setOrder(o);
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
        />
      )}

      <div className="grid cols-3">
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
        <div className="grid cols-3">
          <div className="card">
            <h2>Mandatory items</h2>
            {order.mandatoryItems.length === 0 ? (
              <p className="empty">None recorded.</p>
            ) : (
              order.mandatoryItems.map((m) => (
                <div className="row" key={m.itemKey}>
                  <span className="k">{m.itemKey}: {m.value}</span>
                  <span className="v">{m.quantity} × ₹{m.unitCost.toFixed(2)}</span>
                </div>
              ))
            )}
          </div>
          <div className="card">
            <h2>Add-ons</h2>
            {order.addOns.length === 0 ? (
              <p className="empty">None.</p>
            ) : (
              order.addOns.map((a, i) => (
                <div className="row" key={i}>
                  <span className="k">{a.name}</span>
                  <span className="v">{a.quantity} × ₹{a.unitCost.toFixed(2)}</span>
                </div>
              ))
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
            <div className="card" key={v.variantId} style={{ marginBottom: 12 }}>
              <div className="toolbar">
                <strong>{v.label}</strong>
                <span className="muted">Qty {v.quantity}</span>
                <span className="spacer" />
                <span>₹{v.perUnitCost.toFixed(2)}/unit</span>
                <span>Total ₹{v.totalCost.toFixed(2)}</span>
              </div>
              <div className="grid cols-3">
                <div className="card" style={{ background: "var(--bg-elev-2)" }}>
                  <h2>Mandatory items</h2>
                  {v.mandatoryItems.length === 0 ? (
                    <p className="empty">None.</p>
                  ) : (
                    v.mandatoryItems.map((m) => (
                      <div className="row" key={m.itemKey}>
                        <span className="k">{m.itemKey}: {m.value}</span>
                        <span className="v">{m.quantity} × ₹{m.unitCost.toFixed(2)}</span>
                      </div>
                    ))
                  )}
                </div>
                <div className="card" style={{ background: "var(--bg-elev-2)" }}>
                  <h2>Split across creators</h2>
                  {v.splitAllocation.length === 0 ? (
                    <p className="empty">Nobody assigned yet.</p>
                  ) : (
                    v.splitAllocation.map((s) => (
                      <div className="row" key={s.creatorId}>
                        <span className="k">{creatorName(s.creatorId)}</span>
                        <span className="v">{s.quantityAssigned}</span>
                      </div>
                    ))
                  )}
                </div>
                <div className="card" style={{ background: "var(--bg-elev-2)" }}>
                  <h2>Progress</h2>
                  {v.splitAllocation.length === 0 ? (
                    <p className="empty">Nobody assigned yet.</p>
                  ) : (
                    splitTrackedStageKeys.map((stageKey) => (
                      <div key={stageKey}>
                        <label className="muted" style={{ fontSize: 11, textTransform: "uppercase" }}>
                          {stageKey}
                        </label>
                        {v.splitAllocation.map((s) => {
                          const entry = s.stageProgress.find((sp) => sp.stageKey === stageKey);
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
                                      groupId, order.id, v.variantId, s.creatorId, stageKey, Number(e.target.value)
                                    )
                                  )
                                }
                              />
                            </div>
                          );
                        })}
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          ))}

          <div className="card" style={{ marginBottom: 16 }}>
            <h2>Batch-tracked stages</h2>
            {order.bulkDetails?.stageProgress.map((sp) => (
              <div className="row" key={sp.stageKey}>
                <span className="k">{sp.stageKey}</span>
                <span className="v">
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
                  <span className="muted"> / {sp.totalUnits}</span>
                </span>
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
        </div>
      )}

      <div className="grid cols-2">
        <div className="card cost-card" style={{ background: "var(--bg-elev-2)" }}>
          <h2>Cost &amp; time</h2>
          {order.orderType === "INDIVIDUAL" && order.costEstimate ? (
            <>
              {order.costEstimate.itemizedBreakdown.map((b) => (
                <div className="row" key={b.label}><span className="k">{b.label}</span><span className="v">₹{b.amount.toFixed(2)}</span></div>
              ))}
              <div className="cost-total"><span className="k">Final price</span><span className="v">₹{order.costEstimate.finalCost.toFixed(2)}</span></div>
              <div className="row" style={{ marginTop: 8 }}><span className="k">Crafting time</span><span className="v">{order.costEstimate.grossTimeHours}h</span></div>
            </>
          ) : (
            <>
              <div className="row"><span className="k">Total quantity</span><span className="v">{order.bulkDetails?.totalQuantity}</span></div>
              <div className="cost-total"><span className="k">Total cost</span><span className="v">₹{order.bulkDetails?.totalFinalCost.toFixed(2)}</span></div>
              <div className="row" style={{ marginTop: 8 }}><span className="k">Total time</span><span className="v">{order.bulkDetails?.totalTimeHours}h</span></div>
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
