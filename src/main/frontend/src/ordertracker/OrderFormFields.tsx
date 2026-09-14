import type { BusinessConfig } from "./types";

export interface MandatoryItemDraft {
  itemKey: string;
  value: string;
  quantity: number;
  unitCost: number;
}

export interface LineItemDraft {
  name: string;
  quantity: number;
  unitCost: number;
  unitTimeHours: number;
}

export interface SplitDraft {
  creatorId: string;
  quantityAssigned: number;
}

export interface VariantDraft {
  variantId?: string;
  label: string;
  quantity: number;
  mandatoryItems: MandatoryItemDraft[];
  addOns: LineItemDraft[];
  craftingTimeHours: number;
  splitAllocation: SplitDraft[];
}

export function blankMandatoryItems(config: BusinessConfig): MandatoryItemDraft[] {
  return config.mandatoryItemTypes.map((it) => ({ itemKey: it.itemKey, value: "", quantity: 1, unitCost: 0 }));
}

export function blankVariant(config: BusinessConfig): VariantDraft {
  return {
    label: "",
    quantity: 1,
    mandatoryItems: blankMandatoryItems(config),
    addOns: [],
    craftingTimeHours: 0,
    splitAllocation: [],
  };
}

/** "+ Add variant" clones the currently-last variant's full setup (mockup's own
 *  behaviour: "edit only what differs, typically label/color and quantity") instead of
 *  handing back a blank form every time. Drops variantId so the clone is treated as new. */
export function duplicateVariant(source: VariantDraft): VariantDraft {
  return {
    label: source.label,
    quantity: source.quantity,
    mandatoryItems: source.mandatoryItems.map((m) => ({ ...m })),
    addOns: source.addOns.map((a) => ({ ...a })),
    craftingTimeHours: source.craftingTimeHours,
    splitAllocation: source.splitAllocation.map((s) => ({ ...s })),
  };
}

export function MandatoryItemsFields({
  items,
  onChange,
}: {
  items: MandatoryItemDraft[];
  onChange: (items: MandatoryItemDraft[]) => void;
}) {
  return (
    <div className="grid cols-3">
      {items.map((it, i) => (
        <div className="card" key={it.itemKey} style={{ background: "var(--bg-elev-2)" }}>
          <label className="muted" style={{ fontSize: 12 }}>
            {it.itemKey}
          </label>
          <input
            value={it.value}
            onChange={(e) => onChange(items.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))}
            placeholder="e.g. Cream, 50g"
            style={{ marginBottom: 8 }}
          />
          <div className="form-grid">
            <div>
              <label className="muted" style={{ fontSize: 11 }}>
                Quantity
              </label>
              <input
                type="number"
                min={0}
                step={0.5}
                value={it.quantity}
                onChange={(e) => onChange(items.map((x, j) => (j === i ? { ...x, quantity: Number(e.target.value) } : x)))}
              />
            </div>
            <div>
              <label className="muted" style={{ fontSize: 11 }}>
                Unit cost
              </label>
              <input
                type="number"
                min={0}
                value={it.unitCost}
                onChange={(e) => onChange(items.map((x, j) => (j === i ? { ...x, unitCost: Number(e.target.value) } : x)))}
              />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

export function AddOnsFields({ addOns, onChange }: { addOns: LineItemDraft[]; onChange: (a: LineItemDraft[]) => void }) {
  return (
    <div>
      {addOns.map((a, i) => (
        <div className="card" key={i} style={{ background: "var(--bg-elev-2)", marginBottom: 8 }}>
          <div className="form-grid">
            <div className="form-row">
              <label className="muted" style={{ fontSize: 11 }}>
                Name
              </label>
              <input value={a.name} onChange={(e) => onChange(addOns.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))} />
            </div>
            <div className="form-row">
              <label className="muted" style={{ fontSize: 11 }}>
                Quantity
              </label>
              <input
                type="number"
                min={0}
                step={0.5}
                value={a.quantity}
                onChange={(e) => onChange(addOns.map((x, j) => (j === i ? { ...x, quantity: Number(e.target.value) } : x)))}
              />
            </div>
            <div className="form-row">
              <label className="muted" style={{ fontSize: 11 }}>
                Unit cost
              </label>
              <input
                type="number"
                min={0}
                value={a.unitCost}
                onChange={(e) => onChange(addOns.map((x, j) => (j === i ? { ...x, unitCost: Number(e.target.value) } : x)))}
              />
            </div>
            <div className="form-row">
              <label className="muted" style={{ fontSize: 11 }}>
                Time/unit (hours)
              </label>
              <input
                type="number"
                min={0}
                step={0.05}
                value={a.unitTimeHours}
                onChange={(e) => onChange(addOns.map((x, j) => (j === i ? { ...x, unitTimeHours: Number(e.target.value) } : x)))}
              />
            </div>
          </div>
          <button type="button" onClick={() => onChange(addOns.filter((_, j) => j !== i))}>
            Remove
          </button>
        </div>
      ))}
      <button type="button" onClick={() => onChange([...addOns, { name: "", quantity: 1, unitCost: 0, unitTimeHours: 0 }])}>
        + Add add-on
      </button>
    </div>
  );
}

/** Validates that every variant's creator split adds up to its own quantity. Returns an
 *  error message for the first mismatch found, or null if everything lines up. */
export function validateSplits(variants: VariantDraft[]): string | null {
  for (const v of variants) {
    if (!v.label.trim()) continue;
    const assigned = v.splitAllocation.filter((s) => s.creatorId).reduce((sum, s) => sum + s.quantityAssigned, 0);
    if (v.splitAllocation.length > 0 && assigned !== v.quantity) {
      return `"${v.label}" has a quantity of ${v.quantity} but the creator split adds up to ${assigned} — they must match.`;
    }
  }
  return null;
}
