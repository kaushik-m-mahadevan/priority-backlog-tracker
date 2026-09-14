import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import {
  AddOnsFields,
  MandatoryItemsFields,
  blankMandatoryItems,
  blankVariant,
  duplicateVariant,
  validateSplits,
  type LineItemDraft,
  type MandatoryItemDraft,
  type VariantDraft,
} from "../OrderFormFields";
import type { BusinessConfig, Creator, Customer, OrderType, PatternType, PresetOption } from "../types";

export default function NewOrderPage() {
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const navigate = useNavigate();

  const [config, setConfig] = useState<BusinessConfig | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [creators, setCreators] = useState<Creator[]>([]);
  const [presets, setPresets] = useState<PresetOption[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [orderType, setOrderType] = useState<OrderType>("INDIVIDUAL");
  const [customerId, setCustomerId] = useState("");
  const [createdByCreatorId, setCreatedByCreatorId] = useState("");
  const [itemName, setItemName] = useState("");
  const [orderReceivedDate, setOrderReceivedDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [quotedDeliveryDate, setQuotedDeliveryDate] = useState("");
  const [patternType, setPatternType] = useState<PatternType | "">("");
  const [templateName, setTemplateName] = useState("");
  const [customPatternNotes, setCustomPatternNotes] = useState("");
  const [recipeStepsText, setRecipeStepsText] = useState("");

  // individual-only
  const [mandatoryItems, setMandatoryItems] = useState<MandatoryItemDraft[]>([]);
  const [addOns, setAddOns] = useState<LineItemDraft[]>([]);
  const [craftingTimeHours, setCraftingTimeHours] = useState(0);
  const [packagingPresetId, setPackagingPresetId] = useState("");

  // bulk-only
  const [variants, setVariants] = useState<VariantDraft[]>([]);

  useEffect(() => {
    Promise.all([
      orderTrackerApi.businessConfig(groupId),
      orderTrackerApi.customers(groupId),
      orderTrackerApi.creators(groupId),
      orderTrackerApi.packagingPresets(groupId),
    ]).then(([cfg, c, cr, p]) => {
      setConfig(cfg);
      setCustomers(c);
      setCreators(cr);
      setCustomerId(c[0]?.id ?? "");
      setCreatedByCreatorId(cr[0]?.id ?? "");
      setPresets(p);
      setMandatoryItems(blankMandatoryItems(cfg));
      setVariants([blankVariant(cfg)]);
    });
  }, [groupId]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!customerId || !createdByCreatorId) {
      setError("Pick a customer and who's logging this order");
      return;
    }
    const pattern = patternType
      ? { patternType, templateName: patternType === "TEMPLATE" ? templateName : null,
          customPatternNotes: patternType === "CUSTOM" ? customPatternNotes : null, attachmentUrls: [] }
      : null;
    const recipeSteps = recipeStepsText.split("\n").map((s) => s.trim()).filter(Boolean);

    if (orderType === "BULK") {
      const splitError = validateSplits(variants);
      if (splitError) {
        setError(splitError);
        return;
      }
    }

    try {
      const body: Record<string, unknown> = {
        customerId,
        orderType,
        createdByCreatorId,
        itemName,
        orderReceivedDate: new Date(orderReceivedDate).toISOString(),
        quotedDeliveryDate: quotedDeliveryDate ? new Date(quotedDeliveryDate).toISOString() : null,
        pattern,
        researchItems: [],
        recipeSteps,
      };
      if (orderType === "INDIVIDUAL") {
        body.mandatoryItems = mandatoryItems.filter((m) => m.value.trim());
        body.addOns = addOns.filter((a) => a.name.trim());
        body.packagingPresetId = packagingPresetId || null;
        body.craftingTimeHours = craftingTimeHours;
      } else {
        body.variants = variants
          .filter((v) => v.label.trim())
          .map((v) => ({
            label: v.label,
            quantity: v.quantity,
            mandatoryItems: v.mandatoryItems.filter((m) => m.value.trim()),
            addOns: v.addOns.filter((a) => a.name.trim()),
            craftingTimeHours: v.craftingTimeHours,
            splitAllocation: v.splitAllocation.filter((s) => s.creatorId),
          }));
        body.coordinatingCreatorId = createdByCreatorId;
        body.logisticsBufferDays = 0;
      }
      const created = await orderTrackerApi.createOrder(groupId, body);
      navigate(`/ordertracker/orders/${created.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create order");
    }
  };

  if (!config) return <p className="muted">Loading…</p>;

  if (creators.length === 0) {
    return (
      <div className="card">
        <h1 className="page-title">New order</h1>
        <p>
          You need a creator profile before you can log an order — it's what "Logged by" and creator splits use to
          identify you.
        </p>
        <Link to="/ordertracker/business-settings" className="primary">
          Set up your creator profile
        </Link>
      </div>
    );
  }

  return (
    <div>
      <h1 className="page-title">New order</h1>
      <form className="card" onSubmit={submit}>
        {error && <div className="error">{error}</div>}

        <div className="toolbar" style={{ marginBottom: 16 }}>
          <button type="button" className={orderType === "INDIVIDUAL" ? "primary" : ""} onClick={() => setOrderType("INDIVIDUAL")}>
            Individual order
          </button>
          <button type="button" className={orderType === "BULK" ? "primary" : ""} onClick={() => setOrderType("BULK")}>
            Bulk order
          </button>
        </div>

        <div className="form-grid">
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
            <label>Logged by (creator)</label>
            <select value={createdByCreatorId} onChange={(e) => setCreatedByCreatorId(e.target.value)} required>
              {creators.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="form-row">
          <label>Item name</label>
          <input value={itemName} onChange={(e) => setItemName(e.target.value)} placeholder="e.g. Amigurumi bear" required />
        </div>

        <div className="form-grid">
          <div className="form-row">
            <label>Order received</label>
            <input type="date" value={orderReceivedDate} onChange={(e) => setOrderReceivedDate(e.target.value)} required />
          </div>
          <div className="form-row">
            <label>Quoted delivery (promised to customer)</label>
            <input type="date" value={quotedDeliveryDate} onChange={(e) => setQuotedDeliveryDate(e.target.value)} />
          </div>
        </div>

        <h2 className="settings-section">Pattern</h2>
        <div className="form-row">
          <select value={patternType} onChange={(e) => setPatternType(e.target.value as PatternType | "")}>
            <option value="">No pattern recorded</option>
            <option value="TEMPLATE">Template</option>
            <option value="CUSTOM">Custom</option>
          </select>
        </div>
        {patternType === "TEMPLATE" && (
          <div className="form-row">
            <input value={templateName} onChange={(e) => setTemplateName(e.target.value)} placeholder="Template name" />
          </div>
        )}
        {patternType === "CUSTOM" && (
          <div className="form-row">
            <textarea value={customPatternNotes} onChange={(e) => setCustomPatternNotes(e.target.value)}
              placeholder="Notes kept for recreation" />
          </div>
        )}

        <h2 className="settings-section">Recipe</h2>
        <div className="form-row">
          <textarea value={recipeStepsText} onChange={(e) => setRecipeStepsText(e.target.value)}
            placeholder={"One step per line, e.g.\nCrochet body, attach petals\nInsert safety eyes and stuff"} />
        </div>

        {orderType === "INDIVIDUAL" ? (
          <>
            <h2 className="settings-section">Mandatory items</h2>
            <MandatoryItemsFields items={mandatoryItems} types={config.mandatoryItemTypes} onChange={setMandatoryItems} />

            <h2 className="settings-section">Add-ons</h2>
            <AddOnsFields addOns={addOns} onChange={setAddOns} />

            <h2 className="settings-section">Packaging &amp; crafting time</h2>
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
          </>
        ) : (
          <>
            <h2 className="settings-section">Variants</h2>
            {variants.map((v, i) => {
              const assigned = v.splitAllocation.filter((s) => s.creatorId).reduce((sum, s) => sum + s.quantityAssigned, 0);
              const mismatch = v.splitAllocation.length > 0 && assigned !== v.quantity;
              return (
                <div className="card" key={i} style={{ marginBottom: 16 }}>
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
                    types={config.mandatoryItemTypes}
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
            <button type="button" onClick={() =>
              setVariants((prev) => [...prev, duplicateVariant(prev[prev.length - 1] ?? blankVariant(config))])}>
              + Add variant (copies the last one)
            </button>
          </>
        )}

        <div style={{ marginTop: 20 }}>
          <button className="primary" type="submit">
            Create order
          </button>
        </div>
      </form>
    </div>
  );
}
