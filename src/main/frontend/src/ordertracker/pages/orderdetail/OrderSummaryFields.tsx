import { HoursMinutesInput } from "../../../components/HoursMinutesInput";
import { DELIVERY_TIER_LABELS } from "./deliveryTiers";
import type { AssemblyPreset, Customer, DeliveryTier, PatternType } from "../../types";

/** The identity/pattern/recipe/assembly-packaging block shared byte-for-byte (qd-2) between
 *  EditOrderForm and EditBulkDetailsForm — the only difference between the two was the id
 *  prefix (`eod-`/`ebd-`). Kept out of NewOrderPage on purpose: that page wraps the same
 *  fields in collapsible `<Section>`s, has extra new-customer/required-field UI, and
 *  different placeholder copy, so folding it in too would trade this component's current
 *  low risk for a much larger one (same "two things that only look alike" reasoning that
 *  kept fdup-2 from being merged into a single form). */
export function OrderSummaryFields({
  idPrefix,
  customers,
  customerId,
  onCustomerIdChange,
  itemName,
  onItemNameChange,
  orderReceivedDate,
  onOrderReceivedDateChange,
  quotedDeliveryDate,
  onQuotedDeliveryDateChange,
  deliveryTier,
  onDeliveryTierChange,
  patternType,
  onPatternTypeChange,
  templateName,
  onTemplateNameChange,
  customPatternNotes,
  onCustomPatternNotesChange,
  recipeStepsText,
  onRecipeStepsTextChange,
  researchTimeHours,
  onResearchTimeHoursChange,
  assemblyPackagingInstructions,
  onAssemblyPackagingInstructionsChange,
  assemblyPresets,
  assemblyPresetId,
  onAssemblyPresetIdChange,
}: {
  idPrefix: string;
  customers: Customer[];
  customerId: string;
  onCustomerIdChange: (v: string) => void;
  itemName: string;
  onItemNameChange: (v: string) => void;
  orderReceivedDate: string;
  onOrderReceivedDateChange: (v: string) => void;
  quotedDeliveryDate: string;
  onQuotedDeliveryDateChange: (v: string) => void;
  deliveryTier: DeliveryTier;
  onDeliveryTierChange: (v: DeliveryTier) => void;
  patternType: PatternType | "";
  onPatternTypeChange: (v: PatternType | "") => void;
  templateName: string;
  onTemplateNameChange: (v: string) => void;
  customPatternNotes: string;
  onCustomPatternNotesChange: (v: string) => void;
  recipeStepsText: string;
  onRecipeStepsTextChange: (v: string) => void;
  researchTimeHours: number;
  onResearchTimeHoursChange: (v: number) => void;
  assemblyPackagingInstructions: string;
  onAssemblyPackagingInstructionsChange: (v: string) => void;
  /** mb-4: individual orders only (bulk has no per-variant equivalent) — omitted entirely
   *  by EditBulkDetailsForm, which renders no select at all rather than a disabled one. */
  assemblyPresets?: AssemblyPreset[];
  assemblyPresetId?: string;
  onAssemblyPresetIdChange?: (v: string) => void;
}) {
  return (
    <>
      <div className="form-row">
        <label htmlFor={`${idPrefix}-customer`}>Customer</label>
        <select id={`${idPrefix}-customer`} value={customerId} onChange={(e) => onCustomerIdChange(e.target.value)} required>
          {customers.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>
      <div className="form-row">
        <label htmlFor={`${idPrefix}-item-name`}>Item name</label>
        <input id={`${idPrefix}-item-name`} value={itemName} onChange={(e) => onItemNameChange(e.target.value)} />
      </div>
      <div className="form-grid">
        <div className="form-row">
          <label htmlFor={`${idPrefix}-order-received`}>Order received</label>
          <input
            id={`${idPrefix}-order-received`}
            type="date"
            value={orderReceivedDate}
            onChange={(e) => onOrderReceivedDateChange(e.target.value)}
          />
        </div>
        <div className="form-row">
          <label htmlFor={`${idPrefix}-quoted-delivery`}>Quoted delivery</label>
          <input
            id={`${idPrefix}-quoted-delivery`}
            type="date"
            value={quotedDeliveryDate}
            onChange={(e) => onQuotedDeliveryDateChange(e.target.value)}
          />
        </div>
        <div className="form-row">
          <label htmlFor={`${idPrefix}-delivery-tier`}>Shipping to</label>
          <select
            id={`${idPrefix}-delivery-tier`}
            value={deliveryTier}
            onChange={(e) => onDeliveryTierChange(e.target.value as DeliveryTier)}
          >
            {Object.entries(DELIVERY_TIER_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="form-row">
        <label htmlFor={`${idPrefix}-pattern-type`}>Pattern</label>
        <select
          id={`${idPrefix}-pattern-type`}
          value={patternType}
          onChange={(e) => onPatternTypeChange(e.target.value as PatternType | "")}
        >
          <option value="">No pattern recorded</option>
          <option value="TEMPLATE">Template</option>
          <option value="CUSTOM">Custom</option>
        </select>
      </div>
      {patternType === "TEMPLATE" && (
        <>
          <label htmlFor={`${idPrefix}-template-name`} className="sr-only">
            Template name
          </label>
          <input
            id={`${idPrefix}-template-name`}
            value={templateName}
            onChange={(e) => onTemplateNameChange(e.target.value)}
            placeholder="Template name"
            style={{ marginBottom: 12 }}
          />
        </>
      )}
      {patternType === "CUSTOM" && (
        <>
          <label htmlFor={`${idPrefix}-custom-pattern-notes`} className="sr-only">
            Custom pattern notes
          </label>
          <textarea
            id={`${idPrefix}-custom-pattern-notes`}
            value={customPatternNotes}
            onChange={(e) => onCustomPatternNotesChange(e.target.value)}
            placeholder="Notes kept for recreation"
            style={{ marginBottom: 12 }}
          />
        </>
      )}
      <div className="form-row">
        <label htmlFor={`${idPrefix}-recipe-steps`}>Recipe (one step per line)</label>
        <textarea
          id={`${idPrefix}-recipe-steps`}
          value={recipeStepsText}
          onChange={(e) => onRecipeStepsTextChange(e.target.value)}
        />
      </div>
      <div className="form-row" style={{ maxWidth: 220 }}>
        <span className="muted" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Research time</span>
        <HoursMinutesInput idPrefix={`${idPrefix}-research-time`} hours={researchTimeHours} onChange={onResearchTimeHoursChange} />
      </div>

      <h2 className="settings-section">Assembly &amp; packaging</h2>
      {assemblyPresets && onAssemblyPresetIdChange && (
        <div className="form-row" style={{ maxWidth: 300 }}>
          <label htmlFor={`${idPrefix}-assembly-preset`}>Template (rough cost/time estimate)</label>
          <select
            id={`${idPrefix}-assembly-preset`}
            value={assemblyPresetId ?? ""}
            onChange={(e) => onAssemblyPresetIdChange(e.target.value)}
          >
            <option value="">None</option>
            {assemblyPresets.map((p) => (
              <option key={p.id} value={p.id}>
                {p.label} ({p.estimatedTimeHours}h)
              </option>
            ))}
          </select>
        </div>
      )}
      <div className="form-row">
        <label htmlFor={`${idPrefix}-assembly-packaging`}>How to assemble and pack this order</label>
        <textarea
          id={`${idPrefix}-assembly-packaging`}
          value={assemblyPackagingInstructions}
          onChange={(e) => onAssemblyPackagingInstructionsChange(e.target.value)}
          placeholder="Which materials/tools go where, assembly steps, how it gets boxed up"
        />
      </div>
    </>
  );
}
