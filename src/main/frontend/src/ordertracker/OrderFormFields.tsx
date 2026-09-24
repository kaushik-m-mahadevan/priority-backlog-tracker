import { HoursMinutesInput } from "../components/HoursMinutesInput";
import type { ComponentTemplate, MaterialKind } from "./types";

export interface MandatoryItemDraft {
  kind: MaterialKind;
  value: string;
  quantity: number;
  unitCost: number;
  notes: string;
  linkedYarnTypeId: string | null;
  linkedNeedleTypeId: string | null;
}

/** Minimal shape needed to populate the optional "link to inventory yarn" dropdown —
 *  matches materialinventory's own YarnTypeView, kept as a separate local type so
 *  OrderFormFields doesn't need to import across applet frontend boundaries just for a
 *  display label. */
export interface LinkableYarnType {
  id: string;
  brand: string;
  thickness: string;
  colour: string;
}

/** Same idea as {@link LinkableYarnType}, mirroring materialinventory's NeedleTypeView. */
export interface LinkableNeedleType {
  id: string;
  kind: "CROCHET_HOOK" | "KNITTING_NEEDLE";
  size: string;
}

export interface LineItemDraft {
  name: string;
  quantity: number;
  unitCost: number;
}

export interface SplitDraft {
  creatorId: string;
  quantityAssigned: number;
}

export interface ComponentDraft {
  componentId?: string;
  templateId: string;
  quantity: number;
  mandatoryItems: MandatoryItemDraft[];
  addOns: LineItemDraft[];
  craftingTimeHours: number;
}

export interface VariantDraft {
  variantId?: string;
  label: string;
  quantity: number;
  mandatoryItems: MandatoryItemDraft[];
  addOns: LineItemDraft[];
  components: ComponentDraft[];
  craftingTimeHours: number;
  assemblyTimeHours: number;
  splitAllocation: SplitDraft[];
}

const KIND_LABEL: Record<MaterialKind, string> = { YARN: "Yarn", NEEDLE: "Needle" };

export function blankMandatoryItemEntry(kind: MaterialKind): MandatoryItemDraft {
  return { kind, value: "", quantity: 1, unitCost: 0, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null };
}

/** Exactly two mandatory item kinds exist (design decision: descope the previously
 *  configurable, freeform mandatory-item-type list) — every order starts with one blank
 *  entry of each. */
export function blankMandatoryItems(): MandatoryItemDraft[] {
  return [blankMandatoryItemEntry("YARN"), blankMandatoryItemEntry("NEEDLE")];
}

export function blankVariant(): VariantDraft {
  return {
    label: "",
    quantity: 1,
    mandatoryItems: blankMandatoryItems(),
    addOns: [],
    components: [],
    craftingTimeHours: 0,
    assemblyTimeHours: 0,
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
    components: source.components.map((c) => ({ ...c, componentId: undefined })),
    craftingTimeHours: source.craftingTimeHours,
    assemblyTimeHours: source.assemblyTimeHours,
    splitAllocation: source.splitAllocation.map((s) => ({ ...s })),
  };
}

export function blankComponent(templates: ComponentTemplate[]): ComponentDraft {
  return {
    templateId: templates[0]?.id ?? "",
    quantity: 1,
    mandatoryItems: [],
    addOns: [],
    craftingTimeHours: templates[0]?.baseCraftingTimeHours ?? 0,
  };
}

/** Either kind can appear multiple times per order/variant (e.g. two colors of yarn, or
 *  two needle sizes) — entries are grouped by kind, each group independently extensible.
 *  Each kind links to its own matching Material Inventory catalog (yarn entries to Yarn
 *  Types, needle entries to Needle Types) rather than sharing one generic link field. */
export function MandatoryItemsFields({
  items,
  onChange,
  yarnTypes,
  needleTypes,
}: {
  items: MandatoryItemDraft[];
  onChange: (items: MandatoryItemDraft[]) => void;
  /** Only offered when the business has a linked Material Inventory group — omit or pass
   *  an empty array to hide the dropdown entirely (design decision: opt-in, not required). */
  yarnTypes?: LinkableYarnType[];
  needleTypes?: LinkableNeedleType[];
}) {
  const kinds: MaterialKind[] = ["YARN", "NEEDLE"];

  return (
    <div>
      {kinds.map((kind) => {
        const label = KIND_LABEL[kind];
        const entries = items.filter((it) => it.kind === kind);
        const linkOptions = kind === "YARN" ? yarnTypes : needleTypes;
        return (
          <fieldset key={kind} style={{ marginBottom: 16, border: "none", padding: 0 }}>
            <legend className="muted" style={{ fontSize: 12 }}>
              {label}
            </legend>
            <div className="grid cols-3">
              {entries.map((it, entryIndex) => {
                const globalIndex = items.indexOf(it);
                const valueId = `mi-${kind}-${globalIndex}-value`;
                const qtyId = `mi-${kind}-${globalIndex}-qty`;
                const costId = `mi-${kind}-${globalIndex}-cost`;
                const notesId = `mi-${kind}-${globalIndex}-notes`;
                return (
                  <div className="card" key={globalIndex} style={{ background: "var(--bg-elev-2)" }}>
                    <label htmlFor={valueId} className="sr-only">
                      {label} entry {entryIndex + 1} value
                    </label>
                    <input
                      id={valueId}
                      value={it.value}
                      onChange={(e) =>
                        onChange(items.map((x, j) => (j === globalIndex ? { ...x, value: e.target.value } : x)))
                      }
                      placeholder={kind === "YARN" ? "e.g. Cream, 50g" : "e.g. 4mm crochet hook"}
                      style={{ marginBottom: 8 }}
                    />
                    <div className="form-grid">
                      <div>
                        <label htmlFor={qtyId} className="muted" style={{ fontSize: 11 }}>
                          Quantity
                        </label>
                        <input
                          id={qtyId}
                          type="number"
                          min={0}
                          step={kind === "YARN" ? 0.01 : 1}
                          value={it.quantity}
                          onChange={(e) =>
                            onChange(items.map((x, j) => (j === globalIndex ? { ...x, quantity: Number(e.target.value) } : x)))
                          }
                        />
                      </div>
                      {kind === "YARN" && (
                        <div>
                          <label htmlFor={costId} className="muted" style={{ fontSize: 11 }}>
                            Unit cost
                          </label>
                          <input
                            id={costId}
                            type="number"
                            min={0}
                            value={it.unitCost}
                            onChange={(e) =>
                              onChange(items.map((x, j) => (j === globalIndex ? { ...x, unitCost: Number(e.target.value) } : x)))
                            }
                          />
                        </div>
                      )}
                    </div>
                    <label htmlFor={notesId} className="muted" style={{ fontSize: 11, marginTop: 8, display: "block" }}>
                      Notes (optional)
                    </label>
                    <input
                      id={notesId}
                      value={it.notes}
                      onChange={(e) => onChange(items.map((x, j) => (j === globalIndex ? { ...x, notes: e.target.value } : x)))}
                      placeholder="Why is this mandatory?"
                    />
                    {linkOptions && linkOptions.length > 0 && (
                      <>
                        <label
                          htmlFor={`${valueId}-link`}
                          className="muted"
                          style={{ fontSize: 11, marginTop: 8, display: "block" }}
                        >
                          Link to inventory {kind === "YARN" ? "yarn" : "needle"} (optional)
                        </label>
                        {kind === "YARN" ? (
                          <select
                            id={`${valueId}-link`}
                            value={it.linkedYarnTypeId ?? ""}
                            onChange={(e) =>
                              onChange(
                                items.map((x, j) => (j === globalIndex ? { ...x, linkedYarnTypeId: e.target.value || null } : x))
                              )
                            }
                          >
                            <option value="">Not linked</option>
                            {(yarnTypes ?? []).map((y) => (
                              <option key={y.id} value={y.id}>
                                {y.brand} — {y.thickness}, {y.colour}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <select
                            id={`${valueId}-link`}
                            value={it.linkedNeedleTypeId ?? ""}
                            onChange={(e) =>
                              onChange(
                                items.map((x, j) => (j === globalIndex ? { ...x, linkedNeedleTypeId: e.target.value || null } : x))
                              )
                            }
                          >
                            <option value="">Not linked</option>
                            {(needleTypes ?? []).map((n) => (
                              <option key={n.id} value={n.id}>
                                {n.kind === "CROCHET_HOOK" ? "Hook" : "Needle"} {n.size}
                              </option>
                            ))}
                          </select>
                        )}
                      </>
                    )}
                    {entries.length > 1 && (
                      <button
                        type="button"
                        style={{ marginTop: 8 }}
                        aria-label={`Remove ${label} entry ${entryIndex + 1}`}
                        onClick={() => onChange(items.filter((_, j) => j !== globalIndex))}
                      >
                        Remove
                      </button>
                    )}
                    {entryIndex === entries.length - 1 && (
                      <button
                        type="button"
                        style={{ marginTop: 8, marginLeft: entries.length > 1 ? 8 : 0 }}
                        aria-label={`Add another ${label} entry`}
                        onClick={() => onChange([...items, blankMandatoryItemEntry(kind)])}
                      >
                        + Add another
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </fieldset>
        );
      })}
    </div>
  );
}

export function AddOnsFields({ addOns, onChange }: { addOns: LineItemDraft[]; onChange: (a: LineItemDraft[]) => void }) {
  return (
    <div>
      {addOns.map((a, i) => {
        const nameId = `addon-${i}-name`;
        const qtyId = `addon-${i}-qty`;
        const costId = `addon-${i}-cost`;
        return (
        <div className="card" key={i} style={{ background: "var(--bg-elev-2)", marginBottom: 8 }}>
          <div className="form-grid">
            <div className="form-row">
              <label htmlFor={nameId} className="muted" style={{ fontSize: 11 }}>
                Name
              </label>
              <input id={nameId} value={a.name} onChange={(e) => onChange(addOns.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))} />
            </div>
            <div className="form-row">
              <label htmlFor={qtyId} className="muted" style={{ fontSize: 11 }}>
                Quantity
              </label>
              <input
                id={qtyId}
                type="number"
                min={0}
                step={0.5}
                value={a.quantity}
                onChange={(e) => onChange(addOns.map((x, j) => (j === i ? { ...x, quantity: Number(e.target.value) } : x)))}
              />
            </div>
            <div className="form-row">
              <label htmlFor={costId} className="muted" style={{ fontSize: 11 }}>
                Unit cost
              </label>
              <input
                id={costId}
                type="number"
                min={0}
                value={a.unitCost}
                onChange={(e) => onChange(addOns.map((x, j) => (j === i ? { ...x, unitCost: Number(e.target.value) } : x)))}
              />
            </div>
          </div>
          <button
            type="button"
            aria-label={`Remove add-on ${i + 1}${a.name ? `: ${a.name}` : ""}`}
            onClick={() => onChange(addOns.filter((_, j) => j !== i))}
          >
            Remove
          </button>
        </div>
        );
      })}
      <button type="button" onClick={() => onChange([...addOns, { name: "", quantity: 1, unitCost: 0 }])}>
        + Add add-on
      </button>
    </div>
  );
}

/** Each component is a mini project of its own — a template reference (pattern), its own
 *  materials, its own add-ons, and its own crochet time, multiplied by quantity when
 *  priced. Picking a template pre-fills the crafting time from its typical estimate;
 *  everything else about materials/color/add-ons is entered fresh per order (design
 *  decision — only the pattern and time estimate are reused, not the actual yarn used). */
export function ComponentsFields({
  components,
  onChange,
  templates,
}: {
  components: ComponentDraft[];
  onChange: (components: ComponentDraft[]) => void;
  templates: ComponentTemplate[];
}) {
  return (
    <div>
      {components.map((c, i) => {
        const template = templates.find((t) => t.id === c.templateId);
        return (
          <div className="card" key={i} style={{ background: "var(--bg-elev-2)", marginBottom: 10 }}>
            <div className="form-grid">
              <div className="form-row">
                <label htmlFor={`comp-${i}-template`} className="muted" style={{ fontSize: 11 }}>
                  Component
                </label>
                <select
                  id={`comp-${i}-template`}
                  value={c.templateId}
                  onChange={(e) => {
                    const t = templates.find((x) => x.id === e.target.value);
                    onChange(components.map((x, j) => (j === i
                      ? { ...x, templateId: e.target.value, craftingTimeHours: t?.baseCraftingTimeHours ?? x.craftingTimeHours }
                      : x)));
                  }}
                >
                  <option value="" disabled>Select…</option>
                  {templates.map((t) => (
                    <option key={t.id} value={t.id}>{t.label}</option>
                  ))}
                </select>
              </div>
              <div className="form-row">
                <label htmlFor={`comp-${i}-qty`} className="muted" style={{ fontSize: 11 }}>
                  Quantity
                </label>
                <input
                  id={`comp-${i}-qty`}
                  type="number"
                  min={1}
                  value={c.quantity}
                  onChange={(e) => onChange(components.map((x, j) => (j === i ? { ...x, quantity: Number(e.target.value) } : x)))}
                />
              </div>
              <div className="form-row">
                <span className="muted" style={{ fontSize: 11, display: "block" }}>
                  Crochet time (per unit)
                </span>
                <HoursMinutesInput
                  idPrefix={`comp-${i}-time`}
                  hours={c.craftingTimeHours}
                  onChange={(h) => onChange(components.map((x, j) => (j === i ? { ...x, craftingTimeHours: h } : x)))}
                />
              </div>
            </div>
            {template && (template.pattern?.recipeSteps?.length ?? 0) > 0 && (
              <p className="muted" style={{ fontSize: 12, marginTop: 8 }}>
                Pattern: {template.pattern!.recipeSteps.join(" → ")}
              </p>
            )}

            <div className="muted" style={{ fontSize: 11, marginTop: 10, marginBottom: 4 }}>
              Materials (per unit)
            </div>
            <MandatoryItemsFields
              items={c.mandatoryItems}
              onChange={(items) => onChange(components.map((x, j) => (j === i ? { ...x, mandatoryItems: items } : x)))}
            />
            {c.mandatoryItems.length === 0 && (
              <button type="button" onClick={() => onChange(components.map((x, j) => (j === i ? { ...x, mandatoryItems: blankMandatoryItems() } : x)))}>
                + Add materials
              </button>
            )}

            <div className="muted" style={{ fontSize: 11, marginTop: 10, marginBottom: 4 }}>
              Add-ons (per unit — e.g. floral wire)
            </div>
            <AddOnsFields
              addOns={c.addOns}
              onChange={(a) => onChange(components.map((x, j) => (j === i ? { ...x, addOns: a } : x)))}
            />

            <button
              type="button"
              style={{ marginTop: 10 }}
              aria-label={`Remove component ${i + 1}${template ? `: ${template.label}` : ""}`}
              onClick={() => onChange(components.filter((_, j) => j !== i))}
            >
              Remove component
            </button>
          </div>
        );
      })}
      <button type="button" onClick={() => onChange([...components, blankComponent(templates)])} disabled={templates.length === 0}>
        + Add component
      </button>
      {templates.length === 0 && (
        <p className="muted" style={{ fontSize: 12, marginTop: 6 }}>
          Add a component template under Manage business → Business settings first.
        </p>
      )}
    </div>
  );
}

/** One variant's full editable card — label/quantity, materials/add-ons/components (all
 *  per-unit), processes, and the creator-split editor with its live assigned/mismatch
 *  hint. Shared (qd-2) between NewOrderPage's bulk branch and EditBulkDetailsForm, which
 *  render an identical card, differing only in id prefix and (in the edit form) an
 *  elevated background. */
export function VariantCardFields({
  idPrefix,
  index,
  variant,
  onChange,
  creators,
  yarnTypes,
  needleTypes,
  templates,
  elevated,
}: {
  idPrefix: string;
  index: number;
  variant: VariantDraft;
  onChange: (next: VariantDraft) => void;
  creators: { id: string; name: string }[];
  yarnTypes?: LinkableYarnType[];
  needleTypes?: LinkableNeedleType[];
  templates: ComponentTemplate[];
  elevated?: boolean;
}) {
  const assigned = variant.splitAllocation.filter((s) => s.creatorId).reduce((sum, s) => sum + s.quantityAssigned, 0);
  const mismatch = variant.splitAllocation.length > 0 && assigned !== variant.quantity;
  const labelId = `${idPrefix}-variant-${index}-label`;
  const qtyId = `${idPrefix}-variant-${index}-quantity`;
  const craftId = `${idPrefix}-variant-${index}-crafting-time`;
  const assemblyId = `${idPrefix}-variant-${index}-assembly-time`;

  return (
    <div className="card" style={{ marginBottom: 16, ...(elevated ? { background: "var(--bg-elev-2)" } : {}) }}>
      <div className="form-grid">
        <div className="form-row">
          <label htmlFor={labelId}>Label</label>
          <input id={labelId} value={variant.label} onChange={(e) => onChange({ ...variant, label: e.target.value })} />
        </div>
        <div className="form-row">
          <label htmlFor={qtyId}>Quantity</label>
          <input
            id={qtyId}
            type="number"
            min={1}
            value={variant.quantity}
            onChange={(e) => onChange({ ...variant, quantity: Number(e.target.value) })}
          />
        </div>
      </div>

      <div className="muted" style={{ fontSize: 12, fontWeight: 600 }}>
        Materials
      </div>
      <div className="muted" style={{ fontSize: 12, marginTop: 6 }}>
        Mandatory items (per unit)
      </div>
      <MandatoryItemsFields
        items={variant.mandatoryItems}
        onChange={(items) => onChange({ ...variant, mandatoryItems: items })}
        yarnTypes={yarnTypes}
        needleTypes={needleTypes}
      />

      <div className="muted" style={{ fontSize: 12, marginTop: 12 }}>
        Add-ons (per unit)
      </div>
      <AddOnsFields addOns={variant.addOns} onChange={(a) => onChange({ ...variant, addOns: a })} />

      <div className="muted" style={{ fontSize: 12, fontWeight: 600, marginTop: 16 }}>
        Components (optional)
      </div>
      <ComponentsFields
        components={variant.components}
        onChange={(c) => onChange({ ...variant, components: c })}
        templates={templates}
      />

      <div className="muted" style={{ fontSize: 12, fontWeight: 600, marginTop: 16 }}>
        Processes
      </div>
      <div className="form-grid" style={{ marginTop: 6, maxWidth: 460 }}>
        <div className="form-row">
          <span className="muted" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Crochet time/unit</span>
          <HoursMinutesInput
            idPrefix={craftId}
            hours={variant.craftingTimeHours}
            onChange={(h) => onChange({ ...variant, craftingTimeHours: h })}
          />
        </div>
        <div className="form-row">
          <span className="muted" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Assembly time/unit</span>
          <HoursMinutesInput
            idPrefix={assemblyId}
            hours={variant.assemblyTimeHours}
            onChange={(h) => onChange({ ...variant, assemblyTimeHours: h })}
          />
        </div>
      </div>

      <div className="muted" style={{ fontSize: 12, marginTop: 12 }}>
        Split across creators — must add up to the quantity above ({variant.quantity})
      </div>
      {variant.splitAllocation.map((s, k) => (
        <div className="toolbar" key={k}>
          <select
            aria-label={`Creator for split entry ${k + 1}`}
            value={s.creatorId}
            onChange={(e) =>
              onChange({
                ...variant,
                splitAllocation: variant.splitAllocation.map((sp, l) => (l === k ? { ...sp, creatorId: e.target.value } : sp)),
              })
            }
          >
            <option value="">Select creator</option>
            {creators.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          <input
            aria-label={`Quantity for split entry ${k + 1}`}
            type="number"
            min={1}
            placeholder="Qty"
            value={s.quantityAssigned}
            onChange={(e) =>
              onChange({
                ...variant,
                splitAllocation: variant.splitAllocation.map((sp, l) =>
                  l === k ? { ...sp, quantityAssigned: Number(e.target.value) } : sp
                ),
              })
            }
          />
          <button
            type="button"
            aria-label={`Remove split entry ${k + 1}`}
            onClick={() =>
              onChange({ ...variant, splitAllocation: variant.splitAllocation.filter((_, l) => l !== k) })
            }
          >
            Remove
          </button>
        </div>
      ))}
      <button
        type="button"
        onClick={() =>
          onChange({ ...variant, splitAllocation: [...variant.splitAllocation, { creatorId: "", quantityAssigned: 1 }] })
        }
      >
        + Add creator split
      </button>
      <div className="form-row">
        <p className={mismatch ? "hint bad" : "hint"} style={{ marginTop: 6 }}>
          Assigned so far: {assigned} / {variant.quantity}
        </p>
      </div>
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
