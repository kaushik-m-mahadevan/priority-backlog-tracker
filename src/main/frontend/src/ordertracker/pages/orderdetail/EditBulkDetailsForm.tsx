import { useEffect, useState } from "react";
import { orderTrackerApi } from "../../api";
import { useLinkedNeedleTypes } from "../../useLinkedNeedleTypes";
import { useLinkedYarnTypes } from "../../useLinkedYarnTypes";
import {
  VariantCardFields,
  blankVariant,
  duplicateVariant,
  validateSplits,
  type VariantDraft,
} from "../../OrderFormFields";
import { OrderSummaryFields } from "./OrderSummaryFields";
import type { BusinessConfig, ComponentTemplate, Creator, Customer, DeliveryTier, OrderView, PatternType } from "../../types";

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
  const [patternType, setPatternType] = useState<PatternType | "">(order.pattern?.patternType ?? "");
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

      <OrderSummaryFields
        idPrefix="ebd"
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
      {variants.map((v, i) => (
        <VariantCardFields
          key={i}
          idPrefix="ebd"
          index={i}
          variant={v}
          onChange={(next) => setVariants((prev) => prev.map((x, j) => (j === i ? next : x)))}
          creators={creators}
          yarnTypes={linkedYarnTypes}
          needleTypes={linkedNeedleTypes}
          templates={componentTemplates}
          elevated
        />
      ))}
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
