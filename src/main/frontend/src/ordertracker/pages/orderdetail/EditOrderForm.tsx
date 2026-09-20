import { useEffect, useState } from "react";
import { orderTrackerApi } from "../../api";
import { useLinkedNeedleTypes } from "../../useLinkedNeedleTypes";
import { useLinkedYarnTypes } from "../../useLinkedYarnTypes";
import {
  AddOnsFields,
  ComponentsFields,
  MandatoryItemsFields,
  blankMandatoryItems,
  type ComponentDraft,
  type LineItemDraft,
  type MandatoryItemDraft,
} from "../../OrderFormFields";
import { DELIVERY_TIER_LABELS } from "./deliveryTiers";
import type { BusinessConfig, ComponentTemplate, Customer, DeliveryTier, OrderView, PresetOption } from "../../types";

/** Extracted from OrderDetailPage.tsx (fdup-1) — fully self-contained via props, no
 *  closure over the parent's state. Individual-order edit form; see EditBulkDetailsForm
 *  for the bulk-order equivalent. */
export function EditOrderForm({
  groupId,
  order,
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
  const [components, setComponents] = useState<ComponentDraft[]>(
    order.components.map((c) => ({
      componentId: c.componentId,
      templateId: c.templateId,
      quantity: c.quantity,
      mandatoryItems: c.mandatoryItems.map((m) => ({
        kind: m.kind, value: m.value, quantity: m.quantity, unitCost: m.unitCost, notes: m.notes ?? "",
        linkedYarnTypeId: m.linkedYarnTypeId, linkedNeedleTypeId: m.linkedNeedleTypeId,
      })),
      addOns: c.addOns.map((a) => ({ name: a.name, quantity: a.quantity, unitCost: a.unitCost })),
      craftingTimeHours: c.craftingTimeHours,
    }))
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
        mandatoryItems: mandatoryItems.filter((m) => m.value.trim()),
        addOns: addOns.filter((a) => a.name.trim()),
        components: components.filter((c) => c.templateId).map((c) => ({
          ...c, mandatoryItems: c.mandatoryItems.filter((m) => m.value.trim()), addOns: c.addOns.filter((a) => a.name.trim()),
        })),
        packagingPresetId: packagingPresetId || null,
        itemizedPackaging: order.packaging?.itemizedList ?? [],
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
        <div className="form-row">
          <label htmlFor="eod-delivery-tier">Shipping to</label>
          <select id="eod-delivery-tier" value={deliveryTier} onChange={(e) => setDeliveryTier(e.target.value as DeliveryTier)}>
            {Object.entries(DELIVERY_TIER_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
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

      <h2 className="settings-section">Components (optional)</h2>
      <ComponentsFields components={components} onChange={setComponents} templates={componentTemplates} />

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
