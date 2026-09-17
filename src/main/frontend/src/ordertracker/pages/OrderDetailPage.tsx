import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { formatDate } from "../../lib/format";
import { orderTrackerApi } from "../api";
import { useAuth } from "../../auth/AuthContext";
import { useBusiness } from "../BusinessContext";
import AddToGroupModal from "../AddToGroupModal";
import ImageGallery from "../../components/ImageGallery";
import { TimeStageControl } from "../TimeTracking";
import { useLinkedNeedleTypes } from "../useLinkedNeedleTypes";
import { useLinkedYarnTypes } from "../useLinkedYarnTypes";
import { useMyNeedleInventory } from "../useMyNeedleInventory";
import { useMyYarnInventory } from "../useMyYarnInventory";
import {
  AddOnsFields,
  MandatoryItemsFields,
  blankMandatoryItems,
  duplicateVariant,
  validateSplits,
  type LineItemDraft,
  type MandatoryItemDraft,
  type VariantDraft,
} from "../OrderFormFields";
import type { BusinessConfig, Creator, Customer, OrderFinalizationView, OrderStatus, OrderView, PaymentType, PresetOption, TimeStage } from "../types";

const STATUSES: OrderStatus[] = ["INQUIRY", "CONFIRMED", "IN_PROGRESS", "READY_TO_SHIP", "SHIPPED", "DELIVERED", "CANCELLED"];

/** Work stages are configurable per business, so this is a best-effort visual cue for the
 *  common ones rather than a strict mapping — an unrecognized stageKey still gets a
 *  sensible generic icon rather than nothing. */
const STAGE_ICONS: Record<string, string> = {
  crocheting: "🧶",
  assembly: "🧵",
  packaging: "📦",
  shipment: "🚚",
};
function stageIcon(stageKey: string): string {
  return STAGE_ICONS[stageKey.toLowerCase()] ?? "🔧";
}

/** Collapsible top-level grouping for the order detail view — open by default on wide
 *  screens (so the page reads as one organized document), collapsed by default on
 *  narrow ones (so a phone isn't hit with everything at once). */
function Section({
  title, icon, children, defaultOpen,
}: { title: string; icon: string; children: React.ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(() => {
    if (defaultOpen !== undefined) return defaultOpen;
    return typeof window === "undefined" ? true : window.innerWidth >= 768;
  });
  return (
    <section className="order-section">
      <button type="button" className="order-section-toggle" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        <span className="order-section-title">
          <span aria-hidden="true">{icon}</span> {title}
        </span>
        <span className="order-section-chevron" aria-hidden="true">{open ? "▾" : "▸"}</span>
      </button>
      {open && <div className="order-section-body">{children}</div>}
    </section>
  );
}

function EditOrderForm({
  groupId,
  order,
  config,
  presets,
  customers,
  onSaved,
  onCancel,
}: {
  groupId: string;
  order: OrderView;
  config: BusinessConfig;
  presets: PresetOption[];
  customers: Customer[];
  onSaved: (o: OrderView) => void;
  onCancel: () => void;
}) {
  const linkedYarnTypes = useLinkedYarnTypes(groupId);
  const linkedNeedleTypes = useLinkedNeedleTypes(groupId);
  const [customerId, setCustomerId] = useState(order.customerId);
  const [itemName, setItemName] = useState(order.itemName ?? "");
  const [orderReceivedDate, setOrderReceivedDate] = useState(order.orderReceivedDate?.slice(0, 10) ?? "");
  const [quotedDeliveryDate, setQuotedDeliveryDate] = useState(order.quotedDeliveryDate?.slice(0, 10) ?? "");
  const [patternType, setPatternType] = useState(order.pattern?.patternType ?? "");
  const [templateName, setTemplateName] = useState(order.pattern?.templateName ?? "");
  const [customPatternNotes, setCustomPatternNotes] = useState(order.pattern?.customPatternNotes ?? "");
  const [recipeStepsText, setRecipeStepsText] = useState((order.pattern?.recipeSteps ?? []).join("\n"));
  const [assemblyPackagingInstructions, setAssemblyPackagingInstructions] = useState(order.assemblyPackagingInstructions ?? "");
  const [notes, setNotes] = useState(order.notes ?? "");
  const [mandatoryItems, setMandatoryItems] = useState<MandatoryItemDraft[]>(
    order.mandatoryItems.length > 0
      ? order.mandatoryItems.map((m) => ({
          kind: m.kind, value: m.value, quantity: m.quantity, unitCost: m.unitCost, notes: m.notes ?? "",
          linkedYarnTypeId: m.linkedYarnTypeId, linkedNeedleTypeId: m.linkedNeedleTypeId,
        }))
      : blankMandatoryItems()
  );
  const [addOns, setAddOns] = useState<LineItemDraft[]>(
    order.addOns.map((a) => ({ name: a.name, quantity: a.quantity, unitCost: a.unitCost }))
  );
  const [craftingTimeHours, setCraftingTimeHours] = useState(order.craftingTimeHours);
  const [assemblyTimeHours, setAssemblyTimeHours] = useState(order.assemblyTimeHours);
  const [researchTimeHours, setResearchTimeHours] = useState(order.researchTimeHours);
  const [packagingPresetId, setPackagingPresetId] = useState(order.packaging?.tentativePresetId ?? "");
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const updated = await orderTrackerApi.updateOrder(groupId, order.id, {
        customerId,
        itemName,
        orderReceivedDate: orderReceivedDate ? new Date(orderReceivedDate).toISOString() : null,
        quotedDeliveryDate: quotedDeliveryDate ? new Date(quotedDeliveryDate).toISOString() : null,
        pattern: (() => {
          const recipeSteps = recipeStepsText.split("\n").map((s) => s.trim()).filter(Boolean);
          return patternType || recipeSteps.length > 0
            ? { patternType: patternType || null, templateName: patternType === "TEMPLATE" ? templateName : null,
                customPatternNotes: patternType === "CUSTOM" ? customPatternNotes : null, attachmentUrls: [], recipeSteps }
            : null;
        })(),
        researchItems: order.researchItems,
        researchTimeHours,
        assemblyPackagingInstructions: assemblyPackagingInstructions.trim() || null,
        notes: notes.trim() || null,
        mandatoryItems: mandatoryItems.filter((m) => m.value.trim()),
        addOns: addOns.filter((a) => a.name.trim()),
        packagingPresetId: packagingPresetId || null,
        itemizedPackaging: [],
        craftingTimeHours,
        assemblyTimeHours,
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
        <label htmlFor="eod-customer">Customer</label>
        <select id="eod-customer" value={customerId} onChange={(e) => setCustomerId(e.target.value)} required>
          {customers.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>
      <div className="form-row">
        <label htmlFor="eod-item-name">Item name</label>
        <input id="eod-item-name" value={itemName} onChange={(e) => setItemName(e.target.value)} />
      </div>
      <div className="form-grid">
        <div className="form-row">
          <label htmlFor="eod-order-received">Order received</label>
          <input id="eod-order-received" type="date" value={orderReceivedDate} onChange={(e) => setOrderReceivedDate(e.target.value)} />
        </div>
        <div className="form-row">
          <label htmlFor="eod-quoted-delivery">Quoted delivery</label>
          <input id="eod-quoted-delivery" type="date" value={quotedDeliveryDate} onChange={(e) => setQuotedDeliveryDate(e.target.value)} />
        </div>
      </div>
      <div className="form-row">
        <label htmlFor="eod-pattern-type">Pattern</label>
        <select id="eod-pattern-type" value={patternType} onChange={(e) => setPatternType(e.target.value as never)}>
          <option value="">No pattern recorded</option>
          <option value="TEMPLATE">Template</option>
          <option value="CUSTOM">Custom</option>
        </select>
      </div>
      {patternType === "TEMPLATE" && (
        <>
          <label htmlFor="eod-template-name" className="sr-only">
            Template name
          </label>
          <input id="eod-template-name" value={templateName} onChange={(e) => setTemplateName(e.target.value)} placeholder="Template name" style={{ marginBottom: 12 }} />
        </>
      )}
      {patternType === "CUSTOM" && (
        <>
          <label htmlFor="eod-custom-pattern-notes" className="sr-only">
            Custom pattern notes
          </label>
          <textarea id="eod-custom-pattern-notes" value={customPatternNotes} onChange={(e) => setCustomPatternNotes(e.target.value)}
            placeholder="Notes kept for recreation" style={{ marginBottom: 12 }} />
        </>
      )}
      <div className="form-row">
        <label htmlFor="eod-recipe-steps">Recipe (one step per line)</label>
        <textarea id="eod-recipe-steps" value={recipeStepsText} onChange={(e) => setRecipeStepsText(e.target.value)} />
      </div>
      <div className="form-row" style={{ maxWidth: 220 }}>
        <label htmlFor="eod-research-time">Research time (hours)</label>
        <input id="eod-research-time" type="number" min={0} step={0.25} value={researchTimeHours}
          onChange={(e) => setResearchTimeHours(Number(e.target.value))} />
      </div>

      <h2 className="settings-section">Assembly &amp; packaging</h2>
      <div className="form-row">
        <label htmlFor="eod-assembly-packaging">How to assemble and pack this order</label>
        <textarea id="eod-assembly-packaging" value={assemblyPackagingInstructions}
          onChange={(e) => setAssemblyPackagingInstructions(e.target.value)}
          placeholder="Which materials/tools go where, assembly steps, how it gets boxed up" />
      </div>

      <h2 className="settings-section">Materials</h2>
      <div className="muted" style={{ fontSize: 12, marginBottom: 6 }}>
        Mandatory items
      </div>
      <MandatoryItemsFields
        items={mandatoryItems}
        onChange={setMandatoryItems}
        yarnTypes={linkedYarnTypes}
        needleTypes={linkedNeedleTypes}
      />

      <div className="muted" style={{ fontSize: 12, margin: "12px 0 6px" }}>
        Add-ons
      </div>
      <AddOnsFields addOns={addOns} onChange={setAddOns} />

      <h2 className="settings-section">Packaging</h2>
      <div className="form-row" style={{ maxWidth: 300 }}>
        <label htmlFor="eod-packaging-preset">Packaging preset</label>
        <select id="eod-packaging-preset" value={packagingPresetId} onChange={(e) => setPackagingPresetId(e.target.value)}>
          <option value="">None</option>
          {presets.map((p) => (
            <option key={p.id} value={p.id}>
              {p.label}
            </option>
          ))}
        </select>
      </div>

      <h2 className="settings-section">Processes</h2>
      <div className="form-grid">
        <div className="form-row">
          <label htmlFor="eod-crafting-time">Crochet time (hours)</label>
          <input id="eod-crafting-time" type="number" min={0} step={0.25} value={craftingTimeHours}
            onChange={(e) => setCraftingTimeHours(Number(e.target.value))} />
        </div>
        <div className="form-row">
          <label htmlFor="eod-assembly-time">Assembly time (hours)</label>
          <input id="eod-assembly-time" type="number" min={0} step={0.25} value={assemblyTimeHours}
            onChange={(e) => setAssemblyTimeHours(Number(e.target.value))} />
        </div>
      </div>

      <h2 className="settings-section">Notes</h2>
      <div className="form-row">
        <label htmlFor="eod-notes">Notes — customer interactions, changes, anything else</label>
        <textarea id="eod-notes" value={notes} onChange={(e) => setNotes(e.target.value)}
          placeholder="Customer interactions, changes mid-order, or anything else that doesn't fit above" />
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
  config,
  creators,
  customers,
  onSaved,
  onCancel,
}: {
  groupId: string;
  order: OrderView;
  config: BusinessConfig;
  creators: Creator[];
  customers: Customer[];
  onSaved: (o: OrderView) => void;
  onCancel: () => void;
}) {
  const linkedYarnTypes = useLinkedYarnTypes(groupId);
  const linkedNeedleTypes = useLinkedNeedleTypes(groupId);
  const [customerId, setCustomerId] = useState(order.customerId);
  const [itemName, setItemName] = useState(order.itemName ?? "");
  const [orderReceivedDate, setOrderReceivedDate] = useState(order.orderReceivedDate?.slice(0, 10) ?? "");
  const [quotedDeliveryDate, setQuotedDeliveryDate] = useState(order.quotedDeliveryDate?.slice(0, 10) ?? "");
  const [patternType, setPatternType] = useState(order.pattern?.patternType ?? "");
  const [templateName, setTemplateName] = useState(order.pattern?.templateName ?? "");
  const [customPatternNotes, setCustomPatternNotes] = useState(order.pattern?.customPatternNotes ?? "");
  const [recipeStepsText, setRecipeStepsText] = useState((order.pattern?.recipeSteps ?? []).join("\n"));
  const [assemblyPackagingInstructions, setAssemblyPackagingInstructions] = useState(order.assemblyPackagingInstructions ?? "");
  const [notes, setNotes] = useState(order.notes ?? "");
  const [researchTimeHours, setResearchTimeHours] = useState(order.researchTimeHours);
  const [variants, setVariants] = useState<VariantDraft[]>(
    (order.bulkDetails?.variants ?? []).map((v) => ({
      variantId: v.variantId,
      label: v.label,
      quantity: v.quantity,
      mandatoryItems: v.mandatoryItems.map((m) => ({
        kind: m.kind, value: m.value, quantity: m.quantity, unitCost: m.unitCost, notes: m.notes ?? "",
        linkedYarnTypeId: m.linkedYarnTypeId, linkedNeedleTypeId: m.linkedNeedleTypeId,
      })),
      addOns: v.addOns.map((a) => ({ name: a.name, quantity: a.quantity, unitCost: a.unitCost })),
      craftingTimeHours: v.craftingTimeHours,
      assemblyTimeHours: v.assemblyTimeHours,
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
        customerId,
        itemName,
        orderReceivedDate: orderReceivedDate ? new Date(orderReceivedDate).toISOString() : null,
        quotedDeliveryDate: quotedDeliveryDate ? new Date(quotedDeliveryDate).toISOString() : null,
        pattern: (() => {
          const recipeSteps = recipeStepsText.split("\n").map((s) => s.trim()).filter(Boolean);
          return patternType || recipeSteps.length > 0
            ? { patternType: patternType || null, templateName: patternType === "TEMPLATE" ? templateName : null,
                customPatternNotes: patternType === "CUSTOM" ? customPatternNotes : null, attachmentUrls: [], recipeSteps }
            : null;
        })(),
        researchItems: order.researchItems,
        researchTimeHours,
        assemblyPackagingInstructions: assemblyPackagingInstructions.trim() || null,
        notes: notes.trim() || null,
        variants: variants
          .filter((v) => v.label.trim())
          .map((v) => ({
            variantId: v.variantId ?? null,
            label: v.label,
            quantity: v.quantity,
            mandatoryItems: v.mandatoryItems.filter((m) => m.value.trim()),
            addOns: v.addOns.filter((a) => a.name.trim()),
            craftingTimeHours: v.craftingTimeHours,
            assemblyTimeHours: v.assemblyTimeHours,
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

      <div className="form-row">
        <label htmlFor="ebd-customer">Customer</label>
        <select id="ebd-customer" value={customerId} onChange={(e) => setCustomerId(e.target.value)} required>
          {customers.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>
      <div className="form-row">
        <label htmlFor="ebd-item-name">Item name</label>
        <input id="ebd-item-name" value={itemName} onChange={(e) => setItemName(e.target.value)} />
      </div>
      <div className="form-grid">
        <div className="form-row">
          <label htmlFor="ebd-order-received">Order received</label>
          <input id="ebd-order-received" type="date" value={orderReceivedDate} onChange={(e) => setOrderReceivedDate(e.target.value)} />
        </div>
        <div className="form-row">
          <label htmlFor="ebd-quoted-delivery">Quoted delivery</label>
          <input id="ebd-quoted-delivery" type="date" value={quotedDeliveryDate} onChange={(e) => setQuotedDeliveryDate(e.target.value)} />
        </div>
      </div>
      <div className="form-row">
        <label htmlFor="ebd-pattern-type">Pattern</label>
        <select id="ebd-pattern-type" value={patternType} onChange={(e) => setPatternType(e.target.value as never)}>
          <option value="">No pattern recorded</option>
          <option value="TEMPLATE">Template</option>
          <option value="CUSTOM">Custom</option>
        </select>
      </div>
      {patternType === "TEMPLATE" && (
        <>
          <label htmlFor="ebd-template-name" className="sr-only">
            Template name
          </label>
          <input id="ebd-template-name" value={templateName} onChange={(e) => setTemplateName(e.target.value)} placeholder="Template name" style={{ marginBottom: 12 }} />
        </>
      )}
      {patternType === "CUSTOM" && (
        <>
          <label htmlFor="ebd-custom-pattern-notes" className="sr-only">
            Custom pattern notes
          </label>
          <textarea id="ebd-custom-pattern-notes" value={customPatternNotes} onChange={(e) => setCustomPatternNotes(e.target.value)}
            placeholder="Notes kept for recreation" style={{ marginBottom: 12 }} />
        </>
      )}
      <div className="form-row">
        <label htmlFor="ebd-recipe-steps">Recipe (one step per line)</label>
        <textarea id="ebd-recipe-steps" value={recipeStepsText} onChange={(e) => setRecipeStepsText(e.target.value)} />
      </div>
      <div className="form-row" style={{ maxWidth: 220 }}>
        <label htmlFor="ebd-research-time">Research time (hours)</label>
        <input id="ebd-research-time" type="number" min={0} step={0.25} value={researchTimeHours}
          onChange={(e) => setResearchTimeHours(Number(e.target.value))} />
      </div>
      <h2 className="settings-section">Assembly &amp; packaging</h2>
      <div className="form-row">
        <label htmlFor="ebd-assembly-packaging">How to assemble and pack this order</label>
        <textarea id="ebd-assembly-packaging" value={assemblyPackagingInstructions}
          onChange={(e) => setAssemblyPackagingInstructions(e.target.value)}
          placeholder="Which materials/tools go where, assembly steps, how it gets boxed up" />
      </div>

      <h2 className="settings-section">Logistics</h2>
      <div className="form-grid">
        <div className="form-row">
          <label htmlFor="ebd-coordinating-creator">Coordinating creator</label>
          <select id="ebd-coordinating-creator" value={coordinatingCreatorId} onChange={(e) => setCoordinatingCreatorId(e.target.value)}>
            <option value="">None</option>
            {creators.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <label htmlFor="ebd-logistics-buffer">Logistics buffer (days)</label>
          <input id="ebd-logistics-buffer" type="number" min={0} value={logisticsBufferDays} onChange={(e) => setLogisticsBufferDays(Number(e.target.value))} />
        </div>
      </div>

      <h2 className="settings-section">Variants</h2>
      {variants.map((v, i) => {
        const assigned = v.splitAllocation.filter((s) => s.creatorId).reduce((sum, s) => sum + s.quantityAssigned, 0);
        const mismatch = v.splitAllocation.length > 0 && assigned !== v.quantity;
        return (
          <div className="card" key={i} style={{ marginBottom: 16, background: "var(--bg-elev-2)" }}>
            <div className="form-grid">
              <div className="form-row">
                <label htmlFor={`ebd-variant-${i}-label`}>Label</label>
                <input id={`ebd-variant-${i}-label`} value={v.label} onChange={(e) =>
                  setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))} />
              </div>
              <div className="form-row">
                <label htmlFor={`ebd-variant-${i}-quantity`}>Quantity</label>
                <input id={`ebd-variant-${i}-quantity`} type="number" min={1} value={v.quantity} onChange={(e) =>
                  setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, quantity: Number(e.target.value) } : x)))} />
              </div>
            </div>

            <div className="muted" style={{ fontSize: 12, fontWeight: 600 }}>
              Materials
            </div>
            <div className="muted" style={{ fontSize: 12, marginTop: 6 }}>
              Mandatory items (per unit)
            </div>
            <MandatoryItemsFields
              items={v.mandatoryItems}
              onChange={(items) => setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, mandatoryItems: items } : x)))}
              yarnTypes={linkedYarnTypes}
              needleTypes={linkedNeedleTypes}
            />

            <div className="muted" style={{ fontSize: 12, marginTop: 12 }}>
              Add-ons (per unit)
            </div>
            <AddOnsFields
              addOns={v.addOns}
              onChange={(a) => setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, addOns: a } : x)))}
            />

            <div className="muted" style={{ fontSize: 12, fontWeight: 600, marginTop: 16 }}>
              Processes
            </div>
            <div className="form-grid" style={{ marginTop: 6, maxWidth: 460 }}>
              <div className="form-row">
                <label htmlFor={`ebd-variant-${i}-crafting-time`}>Crochet time/unit (hours)</label>
                <input id={`ebd-variant-${i}-crafting-time`} type="number" min={0} step={0.1} value={v.craftingTimeHours} onChange={(e) =>
                  setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, craftingTimeHours: Number(e.target.value) } : x)))} />
              </div>
              <div className="form-row">
                <label htmlFor={`ebd-variant-${i}-assembly-time`}>Assembly time/unit (hours)</label>
                <input id={`ebd-variant-${i}-assembly-time`} type="number" min={0} step={0.1} value={v.assemblyTimeHours} onChange={(e) =>
                  setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, assemblyTimeHours: Number(e.target.value) } : x)))} />
              </div>
            </div>

            <div className="muted" style={{ fontSize: 12, marginTop: 12 }}>
              Split across creators — must add up to the quantity above ({v.quantity})
            </div>
            {v.splitAllocation.map((s, k) => (
              <div className="toolbar" key={k}>
                <select aria-label={`Creator for split entry ${k + 1}`} value={s.creatorId} onChange={(e) =>
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
                <input aria-label={`Quantity for split entry ${k + 1}`} type="number" min={1} placeholder="Qty" value={s.quantityAssigned} onChange={(e) =>
                  setVariants((prev) => prev.map((x, j) => j === i ? {
                    ...x, splitAllocation: x.splitAllocation.map((sp, l) => l === k ? { ...sp, quantityAssigned: Number(e.target.value) } : sp)
                  } : x))} />
                <button type="button" aria-label={`Remove split entry ${k + 1}`} onClick={() =>
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
            <div className="form-row">
              <p className={mismatch ? "hint bad" : "hint"} style={{ marginTop: 6 }}>
                Assigned so far: {assigned} / {v.quantity}
              </p>
            </div>
          </div>
        );
      })}
      <button type="button" onClick={() => setVariants((prev) => [...prev, duplicateVariant(prev[prev.length - 1])])}>
        + Add variant (copies the last one)
      </button>

      <h2 className="settings-section">Notes</h2>
      <div className="form-row">
        <label htmlFor="ebd-notes">Notes — customer interactions, changes, anything else</label>
        <textarea id="ebd-notes" value={notes} onChange={(e) => setNotes(e.target.value)}
          placeholder="Customer interactions, changes mid-order, or anything else that doesn't fit above" />
      </div>

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
  const linkedYarnTypes = useLinkedYarnTypes(groupId);
  const linkedNeedleTypes = useLinkedNeedleTypes(groupId);
  const myYarnInventory = useMyYarnInventory(groupId);
  const myNeedleInventory = useMyNeedleInventory(groupId);
  const [order, setOrder] = useState<OrderView | null>(null);
  const [config, setConfig] = useState<BusinessConfig | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [creators, setCreators] = useState<Creator[]>([]);
  const [presets, setPresets] = useState<PresetOption[]>([]);
  const [editing, setEditing] = useState(false);
  const [paymentAmount, setPaymentAmount] = useState(0);
  const [paymentType, setPaymentType] = useState<PaymentType>("ADVANCE");
  const [addingToGroup, setAddingToGroup] = useState(false);
  const [historyCreatorFilter, setHistoryCreatorFilter] = useState("all");
  const [historyStageFilter, setHistoryStageFilter] = useState("all");

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
  const splitTrackedStages = config.workStages.filter((s) => s.splitTracked);

  const stageLabel = (stage: TimeStage) =>
    stage === "RESEARCH" ? "Research" : stage === "CRAFTING" ? "Crochet" : "Assembly";

  type HistoryRow = { entryId: string; stage: TimeStage; hours: number; date: string; loggedByCreatorId: string; variantLabel: string | null };
  const allTimeEntries: HistoryRow[] = [
    ...order.timeLogEntries.map((e) => ({ ...e, variantLabel: null as string | null })),
    ...(order.bulkDetails?.variants.flatMap((v) =>
      v.timeLogEntries.map((e) => ({ ...e, variantLabel: v.label }))
    ) ?? []),
  ];
  const filteredHistory = allTimeEntries
    .filter((e) => historyCreatorFilter === "all" || e.loggedByCreatorId === historyCreatorFilter)
    .filter((e) => historyStageFilter === "all" || e.stage === historyStageFilter)
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
  const creatorsWithEntries = creators.filter((c) => allTimeEntries.some((e) => e.loggedByCreatorId === c.id));

  const logTime = async (stage: TimeStage, hours: number, variantId?: string) => {
    const updated = await orderTrackerApi.addTimeLogEntry(groupId, order.id, {
      stage, hours, date: null, note: null, variantId: variantId ?? null,
    });
    setOrder(updated);
  };
  const removeTime = async (entryId: string) => {
    const updated = await orderTrackerApi.removeTimeLogEntry(groupId, order.id, entryId);
    setOrder(updated);
  };

  // Order-level "what you need" list — one combined list across all bulk variants (not
  // per-variant), deduped by kind+value; quantities don't matter here, just presence.
  const itemLabel = (kind: "YARN" | "NEEDLE") => (kind === "YARN" ? "Yarn" : "Needle");
  const dedupeByKeyValue = <T extends { kind: string; value: string }>(items: T[]): T[] => {
    const seen = new Set<string>();
    return items.filter((i) => {
      const k = `${i.kind} ${i.value}`;
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  };
  const allMaterials = dedupeByKeyValue(
    order.orderType === "INDIVIDUAL" ? order.mandatoryItems : (order.bulkDetails?.variants ?? []).flatMap((v) => v.mandatoryItems)
  );
  // Shortfall check (design decision: tackled now, opt-in via linkedYarnTypeId /
  // linkedNeedleTypeId) — sums needed quantity across every material entry sharing an
  // inventory type, not deduped like allMaterials above, since two variants each needing
  // some can genuinely need more combined than either alone. Only ever compares against
  // the viewer's own on-hand count (design decision), never a cross-member total.
  const allMaterialEntriesRaw =
    order.orderType === "INDIVIDUAL" ? order.mandatoryItems : (order.bulkDetails?.variants ?? []).flatMap((v) => v.mandatoryItems);
  const neededByYarnType = new Map<string, number>();
  const neededByNeedleType = new Map<string, number>();
  allMaterialEntriesRaw.forEach((m) => {
    if (m.linkedYarnTypeId) {
      neededByYarnType.set(m.linkedYarnTypeId, (neededByYarnType.get(m.linkedYarnTypeId) ?? 0) + m.quantity);
    }
    if (m.linkedNeedleTypeId) {
      neededByNeedleType.set(m.linkedNeedleTypeId, (neededByNeedleType.get(m.linkedNeedleTypeId) ?? 0) + m.quantity);
    }
  });
  const yarnTypeLabel = (id: string) => {
    const y = linkedYarnTypes.find((yt) => yt.id === id);
    return y ? `${y.brand} — ${y.thickness}, ${y.colour}` : "linked yarn";
  };
  const needleTypeLabel = (id: string) => {
    const n = linkedNeedleTypes.find((nt) => nt.id === id);
    return n ? `${n.kind === "CROCHET_HOOK" ? "Hook" : "Needle"} ${n.size}` : "linked needle";
  };

  return (
    <div>
      <div className="toolbar" style={{ marginBottom: 4 }}>
        <span className="mono" style={{ fontSize: 20, fontWeight: 700 }}>
          {order.orderNumber}
        </span>
        <span className="badge">{order.orderType}</span>
        <select
          aria-label="Order status"
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
        <button onClick={() => setAddingToGroup(true)}>Add to Priority Tracker</button>
        {!editing && (
          <button onClick={() => setEditing(true)}>Edit order</button>
        )}
      </div>
      <p className="page-sub">{order.itemName}</p>
      {addingToGroup && <AddToGroupModal order={order} onClose={() => setAddingToGroup(false)} />}

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
          customers={customers}
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
          config={config}
          creators={creators}
          customers={customers}
          onSaved={(o) => {
            setOrder(o);
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
        />
      )}

      <Section title="Summary" icon="📋">
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
          <div className="row" style={{ marginTop: 8, alignItems: "center", flexWrap: "wrap" }}>
            <span className="k">Research time</span>
            <TimeStageControl
              icon="🔍"
              label="Research"
              estimatedHours={order.researchTimeHours}
              entries={order.timeLogEntries.filter((e) => e.stage === "RESEARCH")}
              onLog={(hours) => logTime("RESEARCH", hours)}
            />
          </div>
        </div>

        <div className="card">
          <h2>Recipe</h2>
          {(order.pattern?.recipeSteps ?? []).length === 0 ? (
            <p className="empty">No steps recorded.</p>
          ) : (
            <ol style={{ margin: 0, paddingLeft: 18 }}>
              {(order.pattern?.recipeSteps ?? []).map((s, i) => (
                <li key={i}>{s}</li>
              ))}
            </ol>
          )}
        </div>

        <div className="card">
          <h2>Assembly &amp; packaging</h2>
          {order.assemblyPackagingInstructions ? (
            <p style={{ whiteSpace: "pre-wrap", margin: 0 }}>{order.assemblyPackagingInstructions}</p>
          ) : (
            <p className="empty">Not recorded.</p>
          )}
        </div>

        <div className="card">
          <h2>What you need</h2>
          {allMaterials.length === 0 ? (
            <p className="empty">Nothing recorded yet.</p>
          ) : (
            <>
              <div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>
                Materials
              </div>
              <ul style={{ margin: "0 0 10px", paddingLeft: 18 }}>
                {allMaterials.map((m, i) => (
                  <li key={i}>
                    {itemLabel(m.kind)}: {m.value}
                  </li>
                ))}
              </ul>
              {(neededByYarnType.size > 0 || neededByNeedleType.size > 0) && (
                <>
                  <div className="muted" style={{ fontSize: 12, marginTop: 10, marginBottom: 4 }}>
                    Your inventory
                  </div>
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {Array.from(neededByYarnType.entries()).map(([yarnTypeId, needed]) => {
                      const have = myYarnInventory.get(yarnTypeId) ?? 0;
                      const short = have < needed;
                      return (
                        <li key={`yarn-${yarnTypeId}`}>
                          {yarnTypeLabel(yarnTypeId)}: you have {have}, need {needed}
                          {short && (
                            <strong style={{ color: "var(--urgent)" }}> — short by {(needed - have).toFixed(2)}</strong>
                          )}
                        </li>
                      );
                    })}
                    {Array.from(neededByNeedleType.entries()).map(([needleTypeId, needed]) => {
                      const have = myNeedleInventory.get(needleTypeId) ?? 0;
                      const short = have < needed;
                      return (
                        <li key={`needle-${needleTypeId}`}>
                          {needleTypeLabel(needleTypeId)}: you have {have}, need {needed}
                          {short && (
                            <strong style={{ color: "var(--urgent)" }}> — short by {(needed - have).toFixed(2)}</strong>
                          )}
                        </li>
                      );
                    })}
                  </ul>
                </>
              )}
            </>
          )}
        </div>

        <div className="card">
          <h2>Notes</h2>
          {order.notes ? (
            <p style={{ whiteSpace: "pre-wrap", margin: 0 }}>{order.notes}</p>
          ) : (
            <p className="empty">Nothing noted.</p>
          )}
        </div>
      </div>
      </Section>

      {order.orderType === "INDIVIDUAL" ? (
        <Section title="Materials" icon="🧶">
        <h2 className="settings-section">Materials &amp; packaging</h2>
        <div className="grid cols-3">
          <div className="card">
            <h2>Mandatory items</h2>
            {order.mandatoryItems.length === 0 ? (
              <p className="empty">None recorded.</p>
            ) : (
              order.mandatoryItems.map((m, i) => (
                <div className="row" key={i}>
                  <span className="k">
                    {m.kind === "YARN" ? "Yarn" : "Needle"}: {m.value}
                    {m.notes && <span className="muted"> ({m.notes})</span>}
                  </span>
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
        <h2 className="settings-section">Processes</h2>
        <div className="card">
          <div className="row" style={{ alignItems: "center", flexWrap: "wrap" }}>
            <span className="k">Crochet time</span>
            <TimeStageControl
              icon="🧶"
              label="Crochet"
              estimatedHours={order.craftingTimeHours}
              entries={order.timeLogEntries.filter((e) => e.stage === "CRAFTING")}
              onLog={(hours) => logTime("CRAFTING", hours)}
            />
          </div>
          <div className="row" style={{ alignItems: "center", marginTop: 8, flexWrap: "wrap" }}>
            <span className="k">Assembly time</span>
            <TimeStageControl
              icon="🪡"
              label="Assembly"
              estimatedHours={order.assemblyTimeHours}
              entries={order.timeLogEntries.filter((e) => e.stage === "ASSEMBLY")}
              onLog={(hours) => logTime("ASSEMBLY", hours)}
            />
          </div>
        </div>
        </Section>
      ) : (
        <Section title="Materials" icon="🧶">
          <h2 className="settings-section">Variants</h2>
          {order.bulkDetails?.variants.map((v) => (
            <div className="card" key={v.variantId} style={{ marginBottom: 12 }}>
              <div className="toolbar">
                <strong>{v.label}</strong>
                <span className="muted">Qty {v.quantity}</span>
                <span className="spacer" />
                <span className="muted">Crochet {v.craftingTimeHours}h/unit · Assembly {v.assemblyTimeHours}h/unit</span>
                <span className="muted" title="Estimated">~{v.perUnitTimeHours.toFixed(2)}h/unit · {v.totalTimeHours.toFixed(2)}h total</span>
                <span title="Estimated">~₹{v.perUnitCost.toFixed(2)}/unit</span>
                <span title="Estimated">Total ~₹{v.totalCost.toFixed(2)}</span>
              </div>
              <div className="toolbar" style={{ marginTop: 8, flexWrap: "wrap", gap: 16 }}>
                <TimeStageControl
                  icon="🧶"
                  label={`Crochet — ${v.label}`}
                  estimatedHours={v.craftingTimeHours}
                  entries={v.timeLogEntries.filter((e) => e.stage === "CRAFTING")}
                  onLog={(hours) => logTime("CRAFTING", hours, v.variantId)}
                />
                <TimeStageControl
                  icon="🪡"
                  label={`Assembly — ${v.label}`}
                  estimatedHours={v.assemblyTimeHours}
                  entries={v.timeLogEntries.filter((e) => e.stage === "ASSEMBLY")}
                  onLog={(hours) => logTime("ASSEMBLY", hours, v.variantId)}
                />
              </div>
              <div className="muted" style={{ fontSize: 12, fontWeight: 600, marginTop: 8, marginBottom: 4 }}>
                Materials
              </div>
              <div className="grid cols-3">
                <div className="card" style={{ background: "var(--bg-elev-2)" }}>
                  <h2>Mandatory items</h2>
                  {v.mandatoryItems.length === 0 ? (
                    <p className="empty">None.</p>
                  ) : (
                    v.mandatoryItems.map((m, i) => (
                      <div className="row" key={i}>
                        <span className="k">
                          {m.kind === "YARN" ? "Yarn" : "Needle"}: {m.value}
                          {m.notes && <span className="muted"> ({m.notes})</span>}
                        </span>
                        <span className="v">{m.quantity} × ₹{m.unitCost.toFixed(2)}</span>
                      </div>
                    ))
                  )}
                </div>
                <div className="card" style={{ background: "var(--bg-elev-2)" }}>
                  <h2>Add-ons</h2>
                  {v.addOns.length === 0 ? (
                    <p className="empty">None.</p>
                  ) : (
                    v.addOns.map((a, i) => (
                      <div className="row" key={i}>
                        <span className="k">{a.name}</span>
                        <span className="v">{a.quantity} × ₹{a.unitCost.toFixed(2)}</span>
                      </div>
                    ))
                  )}
                </div>
              </div>
              <div className="muted" style={{ fontSize: 12, fontWeight: 600, margin: "12px 0 4px" }}>
                Processes
              </div>
              <div className="grid cols-2">
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
                  <h2>Progress by stage</h2>
                  {v.splitAllocation.length === 0 ? (
                    <p className="empty">Nobody assigned yet.</p>
                  ) : (
                    splitTrackedStages.map((stage) => {
                      const stageKey = stage.stageKey;
                      return (
                      <div key={stageKey} style={{ marginBottom: 10 }}>
                        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 4 }}>
                          <span aria-hidden="true">{stageIcon(stageKey)}</span> {stage.label}
                        </div>
                        {v.splitAllocation.map((s) => {
                          const entry = s.stageProgress.find((sp) => sp.stageKey === stageKey);
                          return (
                            <div key={s.creatorId} className="row" style={{ paddingLeft: 22 }}>
                              <span className="k">{creatorName(s.creatorId)}</span>
                              <input
                                aria-label={`${stage.label} units completed by ${creatorName(s.creatorId)}`}
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
                      );
                    })
                  )}
                </div>
              </div>
            </div>
          ))}
        </Section>
      )}

      {order.orderType === "BULK" && (
        <Section title="Assignments" icon="👥">
          <div className="card" style={{ marginBottom: 16 }}>
            <h2>Batch-tracked stages</h2>
            {order.bulkDetails?.stageProgress.map((sp) => {
              const stageLabel = config.workStages.find((s) => s.stageKey === sp.stageKey)?.label ?? sp.stageKey;
              return (
              <div className="row" key={sp.stageKey}>
                <span className="k">
                  <span aria-hidden="true">{stageIcon(sp.stageKey)}</span> {stageLabel}
                </span>
                <span className="v">
                  <input
                    aria-label={`${stageLabel} units completed`}
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
              );
            })}
          </div>

          <div className="card" style={{ marginBottom: 16 }}>
            <h2>Contributor timelines — estimate</h2>
            <p className="muted" style={{ fontSize: 12, marginTop: -4, marginBottom: 10 }}>
              Each creator's own target finish date, based on their assigned hours and their own pace.
            </p>
            {(() => {
              const variants = order.bulkDetails?.variants ?? [];
              const creatorIds = [...new Set(variants.flatMap((v) => v.splitAllocation.map((s) => s.creatorId)))];
              if (creatorIds.length === 0) {
                return <p className="empty">Nobody assigned yet.</p>;
              }
              return creatorIds.map((creatorId) => {
                const hours = variants.reduce((sum, v) => {
                  const split = v.splitAllocation.find((s) => s.creatorId === creatorId);
                  return sum + (split ? split.quantityAssigned * v.perUnitTimeHours : 0);
                }, 0);
                const hoursPerDay = creators.find((c) => c.id === creatorId)?.hoursAvailablePerDay ?? 0;
                const days = hoursPerDay > 0 ? Math.max(1, Math.ceil(hours / hoursPerDay)) : null;
                const target = days !== null && order.orderReceivedDate
                  ? new Date(new Date(order.orderReceivedDate).getTime() + days * 86400000)
                  : null;
                return (
                  <div className="row" key={creatorId}>
                    <span className="k">{creatorName(creatorId)}</span>
                    <span className="v">
                      {hours.toFixed(1)}h at {hoursPerDay}h/day
                      {target && <> — target {formatDate(target.toISOString())}</>}
                    </span>
                  </div>
                );
              });
            })()}
          </div>
        </Section>
      )}

      {order.orderType === "INDIVIDUAL" && (
        <Section title="Assignments" icon="👥">
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
        </Section>
      )}

      <Section title="Cost & time" icon="💰">
        <div className="card cost-card" style={{ background: "var(--bg-elev-2)" }}>
          <h2>Cost &amp; time — estimate</h2>
          <p className="muted" style={{ fontSize: 12, marginTop: -4, marginBottom: 10 }}>
            Calculated from what's entered above. Not a final invoice — record the real numbers once the order ships.
          </p>
          {order.orderType === "INDIVIDUAL" && order.costEstimate ? (
            <>
              {order.costEstimate.itemizedBreakdown.map((b) => (
                <div className="row" key={b.label}><span className="k">{b.label}</span><span className="v">₹{b.amount.toFixed(2)}</span></div>
              ))}
              <div className="cost-total"><span className="k">Estimated price</span><span className="v">₹{order.costEstimate.finalCost.toFixed(2)}</span></div>
              <div className="row" style={{ marginTop: 8 }}><span className="k">Estimated time</span><span className="v">{order.costEstimate.grossTimeHours}h</span></div>
            </>
          ) : (
            <>
              <div className="row"><span className="k">Total quantity</span><span className="v">{order.bulkDetails?.totalQuantity}</span></div>
              <div className="cost-total"><span className="k">Estimated total cost</span><span className="v">₹{order.bulkDetails?.totalFinalCost.toFixed(2)}</span></div>
              <div className="row" style={{ marginTop: 8 }}><span className="k">Estimated total time</span><span className="v">{order.bulkDetails?.totalTimeHours}h</span></div>
            </>
          )}
          <div className="row"><span className="k">Estimated delivery</span>
            <span className="v">{formatDate(dueDate ?? null)}</span></div>
          {order.quotedDeliveryDate && (
            <div className="row"><span className="k">Quoted to customer</span>
              <span className="v">{formatDate(order.quotedDeliveryDate)}</span></div>
          )}
        </div>
      </Section>


      <Section title="Finalization" icon="✅">
        <FinalizationCard groupId={groupId} order={order} />
      </Section>

      <Section title="Photos" icon="📷">
        <div className="card">
          <h2>Finished product photos</h2>
          <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
            One shared gallery for the whole order (design decision) — even a bulk order with several colorways
            keeps one combined set of photos here, same as the materials summary above.
          </p>
          <ImageGallery groupId={groupId} ownerType="order" ownerId={order.id} />
        </div>
      </Section>

      <Section title="Logistics" icon="🚚">
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
            <select aria-label="Payment type" value={paymentType} onChange={(e) => setPaymentType(e.target.value as PaymentType)}>
              <option value="ADVANCE">Advance</option>
              <option value="INSTALLMENT">Installment</option>
              <option value="FINAL">Final</option>
              <option value="REFUND">Refund</option>
            </select>
            <input aria-label="Payment amount" type="number" placeholder="Amount" value={paymentAmount || ""} onChange={(e) => setPaymentAmount(Number(e.target.value))} />
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

        <ShippingCard groupId={groupId} order={order} onUpdated={setOrder} />
      </Section>

      <Section title="Time History" icon="🕒" defaultOpen={false}>
        {allTimeEntries.length === 0 ? (
          <p className="empty">No time logged yet.</p>
        ) : (
          <>
            <div className="toolbar" style={{ marginBottom: 10 }}>
              <select aria-label="Filter by person" value={historyCreatorFilter} onChange={(e) => setHistoryCreatorFilter(e.target.value)}>
                <option value="all">Everyone</option>
                {creatorsWithEntries.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
              <select aria-label="Filter by stage" value={historyStageFilter} onChange={(e) => setHistoryStageFilter(e.target.value)}>
                <option value="all">All stages</option>
                <option value="RESEARCH">Research</option>
                <option value="CRAFTING">Crochet</option>
                <option value="ASSEMBLY">Assembly</option>
              </select>
            </div>
            {filteredHistory.length === 0 ? (
              <p className="empty">No entries match this filter.</p>
            ) : (
              <div className="table-wrap">
                <table className="ot-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Stage</th>
                      <th>Variant</th>
                      <th>Hours</th>
                      <th>Logged by</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredHistory.map((e) => (
                      <tr key={e.entryId}>
                        <td className="cell-subtitle">{new Date(e.date).toLocaleDateString()}</td>
                        <td>{stageLabel(e.stage)}</td>
                        <td className="muted">{e.variantLabel ?? "—"}</td>
                        <td className="cell-order mono">{e.hours.toFixed(2)}h</td>
                        <td>{creatorName(e.loggedByCreatorId)}</td>
                        <td>
                          <button type="button" className="linkbtn" onClick={() => removeTime(e.entryId)}>
                            remove
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </Section>
    </div>
  );
}

/** Every current group member must unanimously accept an order's final actual
 *  cost/revenue before it's treated as settled (same consensus mechanics as the
 *  business's cost-config change requests). Cost/revenue default to the computed
 *  estimate and amount collected so far, adjustable before proposing. */
function FinalizationCard({ groupId, order }: { groupId: string; order: OrderView }) {
  const { user } = useAuth();
  const [finalization, setFinalization] = useState<OrderFinalizationView | null>(null);
  const [finalCost, setFinalCost] = useState(0);
  const [finalRevenue, setFinalRevenue] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const estimatedCost = order.orderType === "INDIVIDUAL"
    ? order.costEstimate?.finalCost ?? 0
    : order.bulkDetails?.totalFinalCost ?? 0;

  const load = () => {
    orderTrackerApi.orderFinalization(groupId, order.id).then((f) => {
      setFinalization(f);
      if (f.status === "NONE") {
        setFinalCost(estimatedCost);
        setFinalRevenue(order.netPaid);
      }
    });
  };

  useEffect(load, [groupId, order.id]);

  if (!finalization) return <p className="muted">Loading…</p>;

  const respond = async (approve: boolean) => {
    setError(null);
    try {
      setFinalization(
        approve
          ? await orderTrackerApi.approveOrderFinalization(groupId, order.id)
          : await orderTrackerApi.rejectOrderFinalization(groupId, order.id)
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to respond");
    }
  };

  const propose = async () => {
    setError(null);
    try {
      setFinalization(await orderTrackerApi.proposeOrderFinalization(groupId, order.id, finalCost, finalRevenue));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to propose");
    }
  };

  return (
    <div className="card">
      <h2>Order finalization</h2>
      <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
        Locks in the order's real cost and revenue once every group member accepts them — used later for profit
        distribution.
      </p>
      {error && <div className="error">{error}</div>}

      {finalization.status === "FINALIZED" && (
        <>
          <div className="row"><span className="k">Final cost</span><span className="v">₹{finalization.finalCost.toFixed(2)}</span></div>
          <div className="row"><span className="k">Final revenue</span><span className="v">₹{finalization.finalRevenue.toFixed(2)}</span></div>
          <div className="cost-total"><span className="k">Final profit</span><span className="v">₹{finalization.finalProfit.toFixed(2)}</span></div>
          <p className="muted" style={{ fontSize: 12 }}>Finalized {formatDate(finalization.finalizedAt)}.</p>
        </>
      )}

      {finalization.status === "PENDING" && (
        <div className="card" style={{ background: "var(--bg-elev-2)" }}>
          <div className="row"><span className="k">Proposed cost</span><span className="v">₹{finalization.finalCost.toFixed(2)}</span></div>
          <div className="row"><span className="k">Proposed revenue</span><span className="v">₹{finalization.finalRevenue.toFixed(2)}</span></div>
          <div className="row"><span className="k">Proposed profit</span><span className="v">₹{finalization.finalProfit.toFixed(2)}</span></div>
          <p className="muted" style={{ fontSize: 13 }}>
            Approved by {finalization.approvedByUserIds.length}/{finalization.groupMemberIds.length} member(s) so far.
          </p>
          {user && finalization.approvedByUserIds.includes(user.id) ? (
            <p className="hint">You've approved this — waiting on everyone else.</p>
          ) : (
            <div className="toolbar">
              <button className="primary" onClick={() => respond(true)}>Approve</button>
              <button onClick={() => respond(false)}>Reject</button>
            </div>
          )}
        </div>
      )}

      {finalization.status === "NONE" && (
        <div className="toolbar" style={{ flexWrap: "wrap" }}>
          <label>
            Final cost
            <input aria-label="Final cost" type="number" value={finalCost} onChange={(e) => setFinalCost(Number(e.target.value))} />
          </label>
          <label>
            Final revenue
            <input aria-label="Final revenue" type="number" value={finalRevenue} onChange={(e) => setFinalRevenue(Number(e.target.value))} />
          </label>
          <button className="primary" onClick={propose}>Propose finalization</button>
        </div>
      )}
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
                <button
                  type="button"
                  aria-label={`Mark stop ${i + 1} (${s.originLocationCode} to ${s.destinationLocationCode}) shipped`}
                  onClick={async () =>
                    onUpdated(await orderTrackerApi.markShipmentStop(groupId, order.id, i, { shippedDate: new Date().toISOString() }))}>
                  Mark shipped
                </button>
              )}
              {!s.deliveredConfirmed && s.shippedDate && (
                <button
                  type="button"
                  style={{ marginLeft: 6 }}
                  aria-label={`Mark stop ${i + 1} (${s.originLocationCode} to ${s.destinationLocationCode}) delivered`}
                  onClick={async () =>
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
          <input aria-label="Origin code" placeholder="Origin code" value={origin} onChange={(e) => setOrigin(e.target.value)} />
          <input aria-label="Destination code" placeholder="Destination code" value={destination} onChange={(e) => setDestination(e.target.value)} />
          <input aria-label="Carrier" placeholder="Carrier" value={carrier} onChange={(e) => setCarrier(e.target.value)} />
          <input aria-label="Tracking number" placeholder="Tracking number" value={tracking} onChange={(e) => setTracking(e.target.value)} />
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
