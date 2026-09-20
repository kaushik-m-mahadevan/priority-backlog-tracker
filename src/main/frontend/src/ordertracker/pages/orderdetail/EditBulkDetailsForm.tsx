import { useEffect, useState } from "react";
import { orderTrackerApi } from "../../api";
import { useLinkedNeedleTypes } from "../../useLinkedNeedleTypes";
import { useLinkedYarnTypes } from "../../useLinkedYarnTypes";
import {
  AddOnsFields,
  ComponentsFields,
  MandatoryItemsFields,
  blankVariant,
  duplicateVariant,
  validateSplits,
  type VariantDraft,
} from "../../OrderFormFields";
import { DELIVERY_TIER_LABELS } from "./deliveryTiers";
import type { BusinessConfig, ComponentTemplate, Creator, Customer, DeliveryTier, OrderView } from "../../types";

/** Extracted from OrderDetailPage.tsx (fdup-1) — fully self-contained via props, no
 *  closure over the parent's state. Bulk-order edit form; see EditOrderForm for the
 *  individual-order equivalent. */
export function EditBulkDetailsForm({
  groupId,
  order,
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
  const [componentTemplates, setComponentTemplates] = useState<ComponentTemplate[]>([]);
  useEffect(() => {
    orderTrackerApi.componentTemplates(groupId).then(setComponentTemplates);
  }, [groupId]);
  const [customerId, setCustomerId] = useState(order.customerId);
  const [itemName, setItemName] = useState(order.itemName ?? "");
  const [orderReceivedDate, setOrderReceivedDate] = useState(order.orderReceivedDate?.slice(0, 10) ?? "");
  const [quotedDeliveryDate, setQuotedDeliveryDate] = useState(order.quotedDeliveryDate?.slice(0, 10) ?? "");
  const [deliveryTier, setDeliveryTier] = useState<DeliveryTier>(order.deliveryTier);
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
      components: v.components.map((c) => ({
        componentId: c.componentId,
        templateId: c.templateId,
        quantity: c.quantity,
        mandatoryItems: c.mandatoryItems.map((m) => ({
          kind: m.kind, value: m.value, quantity: m.quantity, unitCost: m.unitCost, notes: m.notes ?? "",
          linkedYarnTypeId: m.linkedYarnTypeId, linkedNeedleTypeId: m.linkedNeedleTypeId,
        })),
        addOns: c.addOns.map((a) => ({ name: a.name, quantity: a.quantity, unitCost: a.unitCost })),
        craftingTimeHours: c.craftingTimeHours,
      })),
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
        deliveryTier,
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
            components: v.components.filter((c) => c.templateId).map((c) => ({
              ...c, mandatoryItems: c.mandatoryItems.filter((m) => m.value.trim()), addOns: c.addOns.filter((a) => a.name.trim()),
            })),
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
        <div className="form-row">
          <label htmlFor="ebd-delivery-tier">Shipping to</label>
          <select id="ebd-delivery-tier" value={deliveryTier} onChange={(e) => setDeliveryTier(e.target.value as DeliveryTier)}>
            {Object.entries(DELIVERY_TIER_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
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
              Components (optional)
            </div>
            <ComponentsFields
              components={v.components}
              onChange={(c) => setVariants((prev) => prev.map((x, j) => (j === i ? { ...x, components: c } : x)))}
              templates={componentTemplates}
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
      <button type="button" onClick={() => setVariants((prev) => [...prev, duplicateVariant(prev[prev.length - 1] ?? blankVariant())])}>
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
