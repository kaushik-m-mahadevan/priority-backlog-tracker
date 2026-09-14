import type { BusinessConfig, MandatoryItemType } from "./types";

export interface MandatoryItemDraft {
  itemKey: string;
  value: string;
  quantity: number;
  unitCost: number;
  notes: string;
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

export function blankMandatoryItemEntry(itemKey: string): MandatoryItemDraft {
  return { itemKey, value: "", quantity: 1, unitCost: 0, notes: "" };
}

export function blankMandatoryItems(config: BusinessConfig): MandatoryItemDraft[] {
  return config.mandatoryItemTypes.map((it) => blankMandatoryItemEntry(it.itemKey));
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

/** One configured item type can appear multiple times per order/variant (e.g. two colors
 *  of wool, or a hook used alongside a spare) — entries are grouped by itemKey, each group
 *  independently extensible. Tool types (isTool) skip quantity/unit-cost: they're reused
 *  across orders, not purchased or itemized per project. */
export function MandatoryItemsFields({
  items,
  types,
  onChange,
}: {
  items: MandatoryItemDraft[];
  types: MandatoryItemType[];
  onChange: (items: MandatoryItemDraft[]) => void;
}) {
  return (
    <div>
      {types.map((type) => {
        const entries = items.filter((it) => it.itemKey === type.itemKey);
        return (
          <div key={type.itemKey} style={{ marginBottom: 16 }}>
            <label className="muted" style={{ fontSize: 12 }}>
              {type.label} {type.isTool && <span className="hint">(tool)</span>}
            </label>
            <div className="grid cols-3">
              {entries.map((it, entryIndex) => {
                const globalIndex = items.indexOf(it);
                return (
                  <div className="card" key={globalIndex} style={{ background: "var(--bg-elev-2)" }}>
                    <input
                      value={it.value}
                      onChange={(e) =>
                        onChange(items.map((x, j) => (j === globalIndex ? { ...x, value: e.target.value } : x)))
                      }
                      placeholder={type.isTool ? "e.g. 4mm hook" : "e.g. Cream, 50g"}
                      style={{ marginBottom: 8 }}
                    />
                    {!type.isTool && (
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
                            onChange={(e) =>
                              onChange(items.map((x, j) => (j === globalIndex ? { ...x, quantity: Number(e.target.value) } : x)))
                            }
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
                            onChange={(e) =>
                              onChange(items.map((x, j) => (j === globalIndex ? { ...x, unitCost: Number(e.target.value) } : x)))
                            }
                          />
                        </div>
                      </div>
                    )}
                    <label className="muted" style={{ fontSize: 11, marginTop: 8, display: "block" }}>
                      Notes (optional)
                    </label>
                    <input
                      value={it.notes}
                      onChange={(e) => onChange(items.map((x, j) => (j === globalIndex ? { ...x, notes: e.target.value } : x)))}
                      placeholder="Why is this mandatory?"
                    />
                    {entries.length > 1 && (
                      <button
                        type="button"
                        style={{ marginTop: 8 }}
                        onClick={() => onChange(items.filter((_, j) => j !== globalIndex))}
                      >
                        Remove
                      </button>
                    )}
                    {entryIndex === entries.length - 1 && (
                      <button
                        type="button"
                        style={{ marginTop: 8, marginLeft: entries.length > 1 ? 8 : 0 }}
                        onClick={() => onChange([...items, blankMandatoryItemEntry(type.itemKey)])}
                      >
                        + Add another
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
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
    const assignedCreatorIds = v.splitAllocation.filter((s) => s.creatorId).map((s) => s.creatorId);
    const duplicate = assignedCreatorIds.find((id, i) => assignedCreatorIds.indexOf(id) !== i);
    if (duplicate) {
      return `"${v.label}" has the same creator assigned to more than one split entry — each creator can only appear once per variant.`;
    }
    const assigned = v.splitAllocation.filter((s) => s.creatorId).reduce((sum, s) => sum + s.quantityAssigned, 0);
    if (v.splitAllocation.length > 0 && assigned !== v.quantity) {
      return `"${v.label}" has a quantity of ${v.quantity} but the creator split adds up to ${assigned} — they must match.`;
    }
  }
  return null;
}
