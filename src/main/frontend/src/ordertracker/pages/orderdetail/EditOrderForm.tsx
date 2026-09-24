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
import { OrderSummaryFields } from "./OrderSummaryFields";
import { HoursMinutesInput } from "../../../components/HoursMinutesInput";
import type { BusinessConfig, ComponentTemplate, Customer, DeliveryTier, OrderView, PatternType, PresetOption } from "../../types";

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
  const [patternType, setPatternType] = useState<PatternType | "">(order.pattern?.patternType ?? "");
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
      <OrderSummaryFields
        idPrefix="eod"
        customers={customers}
        customerId={customerId}
        onCustomerIdChange={setCustomerId}
        itemName={itemName}
        onItemNameChange={setItemName}
        orderReceivedDate={orderReceivedDate}
        onOrderReceivedDateChange={setOrderReceivedDate}
        quotedDeliveryDate={quotedDeliveryDate}
        onQuotedDeliveryDateChange={setQuotedDeliveryDate}
        deliveryTier={deliveryTier}
        onDeliveryTierChange={setDeliveryTier}
        patternType={patternType}
        onPatternTypeChange={setPatternType}
        templateName={templateName}
        onTemplateNameChange={setTemplateName}
        customPatternNotes={customPatternNotes}
        onCustomPatternNotesChange={setCustomPatternNotes}
        recipeStepsText={recipeStepsText}
        onRecipeStepsTextChange={setRecipeStepsText}
        researchTimeHours={researchTimeHours}
        onResearchTimeHoursChange={setResearchTimeHours}
        assemblyPackagingInstructions={assemblyPackagingInstructions}
        onAssemblyPackagingInstructionsChange={setAssemblyPackagingInstructions}
      />

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
          <span className="muted" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Crochet time</span>
          <HoursMinutesInput idPrefix="eod-crafting-time" hours={craftingTimeHours} onChange={setCraftingTimeHours} />
        </div>
        <div className="form-row">
          <span className="muted" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Assembly time</span>
          <HoursMinutesInput idPrefix="eod-assembly-time" hours={assemblyTimeHours} onChange={setAssemblyTimeHours} />
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
