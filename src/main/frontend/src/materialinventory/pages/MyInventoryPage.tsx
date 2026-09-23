import { Fragment, useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import Connections from "../../components/Connections";
import { materialInventoryApi } from "../api";
import { useMaterialInventory } from "../MaterialInventoryContext";
import { formatMoney } from "../../lib/format";
import type { InventoryEntryView, NeedleInventoryEntryView, NeedleKind, NeedleTypeView, YarnTypeView } from "../types";

type NewYarnDraft = {
  brand: string;
  thickness: string;
  colour: string;
  material: string;
  skeinWeightGrams: string;
  skeinLengthMeters: string;
  recommendedHookSize: string;
  notes: string;
  costPerSkein: string;
};

const blankYarnDraft = (): NewYarnDraft => ({
  brand: "",
  thickness: "",
  colour: "",
  material: "",
  skeinWeightGrams: "",
  skeinLengthMeters: "",
  recommendedHookSize: "",
  notes: "",
  costPerSkein: "",
});

type NewNeedleDraft = { kind: NeedleKind; size: string; notes: string };

const blankNeedleDraft = (): NewNeedleDraft => ({ kind: "CROCHET_HOOK", size: "", notes: "" });

/** Everyone's on-hand yarn, business-wide (design decision: full transparency, same
 *  precedent as Finance Tracker's balances) — this member's own row is editable inline,
 *  everyone else's is read-only. Yarn types themselves (brand + thickness + colour) are a
 *  shared, business-wide catalog any member can add to (design decision), so two people
 *  describing the same yarn always point at the same row instead of drifting apart.
 *
 *  Hooks/needles get their own separate section below (design decision) — they're
 *  reusable tools, not a consumable material, tracked in whole units rather than
 *  quarter-skein steps.
 *
 *  ui-2: the yarn identity cell is compact (brand/thickness/colour only) with material
 *  detail, notes, cost, and price history moved behind a per-row expand toggle instead
 *  of always shown — the "reservation in its own section" half of ui-2 is deliberately
 *  not done here: reservation doesn't exist yet (that's ad-2, Order → Material
 *  Inventory decrement), so there's nothing to give a section to until it lands. */
export default function MyInventoryPage() {
  const { currentInventoryGroup, currentGroupId } = useMaterialInventory();
  const { user } = useAuth();
  const [yarnTypes, setYarnTypes] = useState<YarnTypeView[]>([]);
  const [entries, setEntries] = useState<InventoryEntryView[]>([]);
  const [needleTypes, setNeedleTypes] = useState<NeedleTypeView[]>([]);
  const [needleEntries, setNeedleEntries] = useState<NeedleInventoryEntryView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showNewYarn, setShowNewYarn] = useState(false);
  const [newYarn, setNewYarn] = useState<NewYarnDraft>(blankYarnDraft());
  const [editingQuantity, setEditingQuantity] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState<string | null>(null);
  const [editingCost, setEditingCost] = useState<Record<string, string>>({});
  const [savingCost, setSavingCost] = useState<string | null>(null);
  const [showCostHistory, setShowCostHistory] = useState<string | null>(null);
  const [expandedYarn, setExpandedYarn] = useState<Record<string, boolean>>({});
  const [showNewNeedle, setShowNewNeedle] = useState(false);
  const [newNeedle, setNewNeedle] = useState<NewNeedleDraft>(blankNeedleDraft());
  const [editingNeedleQuantity, setEditingNeedleQuantity] = useState<Record<string, string>>({});
  const [savingNeedle, setSavingNeedle] = useState<string | null>(null);

  const members = currentInventoryGroup?.members ?? [];
  const memberName = (id: string) => members.find((m) => m.id === id)?.name ?? "Unknown";

  const load = () => {
    if (!currentGroupId) return;
    setLoading(true);
    Promise.all([
      materialInventoryApi.yarnTypes(currentGroupId),
      materialInventoryApi.inventory(currentGroupId),
      materialInventoryApi.needleTypes(currentGroupId),
      materialInventoryApi.needleInventory(currentGroupId),
    ])
      .then(([types, inv, needles, needleInv]) => {
        setYarnTypes(types);
        setEntries(inv);
        setNeedleTypes(needles);
        setNeedleEntries(needleInv);
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  const entryFor = (yarnTypeId: string, userId: string) =>
    entries.find((e) => e.yarnTypeId === yarnTypeId && e.userId === userId);
  const quantityFor = (yarnTypeId: string, userId: string) => entryFor(yarnTypeId, userId)?.quantity ?? 0;
  const isStale = (yarnTypeId: string, userId: string) => entryFor(yarnTypeId, userId)?.stale ?? false;

  const needleQuantityFor = (needleTypeId: string, userId: string) =>
    needleEntries.find((e) => e.needleTypeId === needleTypeId && e.userId === userId)?.quantity ?? 0;

  const submitNewYarn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentGroupId) return;
    if (!newYarn.brand.trim() || !newYarn.thickness.trim() || !newYarn.colour.trim()) {
      setError("Brand, thickness, and colour are all required");
      return;
    }
    setError(null);
    try {
      await materialInventoryApi.createYarnType(currentGroupId, {
        brand: newYarn.brand.trim(),
        thickness: newYarn.thickness.trim(),
        colour: newYarn.colour.trim(),
        material: newYarn.material.trim() || null,
        skeinWeightGrams: newYarn.skeinWeightGrams.trim() ? Number(newYarn.skeinWeightGrams) : null,
        skeinLengthMeters: newYarn.skeinLengthMeters.trim() ? Number(newYarn.skeinLengthMeters) : null,
        recommendedHookSize: newYarn.recommendedHookSize.trim() || null,
        notes: newYarn.notes.trim() || null,
        costPerSkein: newYarn.costPerSkein.trim() ? Number(newYarn.costPerSkein) : null,
      });
      setNewYarn(blankYarnDraft());
      setShowNewYarn(false);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add yarn type");
    }
  };

  const startEditing = (yarnTypeId: string) => {
    setEditingQuantity({ ...editingQuantity, [yarnTypeId]: String(quantityFor(yarnTypeId, user?.id ?? "")) });
  };

  const saveQuantity = async (yarnTypeId: string) => {
    if (!currentGroupId) return;
    const value = Number(editingQuantity[yarnTypeId]);
    if (Number.isNaN(value) || value < 0) {
      setError("Enter a quantity of zero or more");
      return;
    }
    setError(null);
    setSaving(yarnTypeId);
    try {
      await materialInventoryApi.setMyQuantity(currentGroupId, yarnTypeId, { quantity: value });
      const rest = { ...editingQuantity };
      delete rest[yarnTypeId];
      setEditingQuantity(rest);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update quantity");
    } finally {
      setSaving(null);
    }
  };

  const saveCost = async (y: YarnTypeView) => {
    if (!currentGroupId) return;
    const raw = editingCost[y.id];
    const value = raw.trim() ? Number(raw) : null;
    if (raw.trim() && (Number.isNaN(value) || (value ?? 0) < 0)) {
      setError("Enter a cost of zero or more");
      return;
    }
    setError(null);
    setSavingCost(y.id);
    try {
      await materialInventoryApi.updateYarnType(currentGroupId, y.id, {
        brand: y.brand,
        thickness: y.thickness,
        colour: y.colour,
        material: y.material,
        skeinWeightGrams: y.skeinWeightGrams,
        skeinLengthMeters: y.skeinLengthMeters,
        recommendedHookSize: y.recommendedHookSize,
        notes: y.notes,
        costPerSkein: value,
      });
      const rest = { ...editingCost };
      delete rest[y.id];
      setEditingCost(rest);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update cost");
    } finally {
      setSavingCost(null);
    }
  };

  const removeYarnType = async (yarnTypeId: string) => {
    if (!currentGroupId) return;
    setError(null);
    try {
      await materialInventoryApi.deleteYarnType(currentGroupId, yarnTypeId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to remove yarn type");
    }
  };

  const submitNewNeedle = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentGroupId) return;
    if (!newNeedle.size.trim()) {
      setError("Size is required");
      return;
    }
    setError(null);
    try {
      await materialInventoryApi.createNeedleType(currentGroupId, {
        kind: newNeedle.kind,
        size: newNeedle.size.trim(),
        notes: newNeedle.notes.trim() || null,
      });
      setNewNeedle(blankNeedleDraft());
      setShowNewNeedle(false);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add needle type");
    }
  };

  const startEditingNeedle = (needleTypeId: string) => {
    setEditingNeedleQuantity({ ...editingNeedleQuantity, [needleTypeId]: String(needleQuantityFor(needleTypeId, user?.id ?? "")) });
  };

  const saveNeedleQuantity = async (needleTypeId: string) => {
    if (!currentGroupId) return;
    const value = Number(editingNeedleQuantity[needleTypeId]);
    if (!Number.isInteger(value) || value < 0) {
      setError("Enter a whole number of zero or more");
      return;
    }
    setError(null);
    setSavingNeedle(needleTypeId);
    try {
      await materialInventoryApi.setMyNeedleQuantity(currentGroupId, needleTypeId, { quantity: value });
      const rest = { ...editingNeedleQuantity };
      delete rest[needleTypeId];
      setEditingNeedleQuantity(rest);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update quantity");
    } finally {
      setSavingNeedle(null);
    }
  };

  const removeNeedleType = async (needleTypeId: string) => {
    if (!currentGroupId) return;
    setError(null);
    try {
      await materialInventoryApi.deleteNeedleType(currentGroupId, needleTypeId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to remove needle type");
    }
  };

  return (
    <div>
      <h1 className="page-title">Team inventory</h1>
      <div className="toolbar" style={{ marginBottom: 8 }}>
        <p className="page-sub" style={{ margin: 0 }}>{currentInventoryGroup?.name}</p>
        <span className="spacer" />
        <Connections appletKey="materialinventory" groupId={currentGroupId} />
      </div>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <div className="toolbar">
          <h2 style={{ margin: 0 }}>Yarn types</h2>
          <span className="spacer" />
          {!showNewYarn && (
            <button type="button" onClick={() => setShowNewYarn(true)}>
              + Add yarn type
            </button>
          )}
        </div>

        {showNewYarn && (
          <form onSubmit={submitNewYarn} style={{ marginTop: 12 }}>
            <div className="form-grid">
              <div className="form-row">
                <label htmlFor="yarn-brand">Brand</label>
                <input id="yarn-brand" value={newYarn.brand} onChange={(e) => setNewYarn({ ...newYarn, brand: e.target.value })} required />
              </div>
              <div className="form-row">
                <label htmlFor="yarn-thickness">Thickness</label>
                <input
                  id="yarn-thickness"
                  placeholder="e.g. Worsted (4)"
                  value={newYarn.thickness}
                  onChange={(e) => setNewYarn({ ...newYarn, thickness: e.target.value })}
                  required
                />
              </div>
              <div className="form-row">
                <label htmlFor="yarn-colour">Colour</label>
                <input id="yarn-colour" value={newYarn.colour} onChange={(e) => setNewYarn({ ...newYarn, colour: e.target.value })} required />
              </div>
            </div>
            <div className="form-grid">
              <div className="form-row">
                <label htmlFor="yarn-material">Material (optional)</label>
                <input
                  id="yarn-material"
                  placeholder="e.g. 100% cotton"
                  value={newYarn.material}
                  onChange={(e) => setNewYarn({ ...newYarn, material: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label htmlFor="yarn-weight">Skein weight, grams (optional)</label>
                <input
                  id="yarn-weight"
                  type="number"
                  min={0}
                  value={newYarn.skeinWeightGrams}
                  onChange={(e) => setNewYarn({ ...newYarn, skeinWeightGrams: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label htmlFor="yarn-length">Skein length, meters (optional)</label>
                <input
                  id="yarn-length"
                  type="number"
                  min={0}
                  value={newYarn.skeinLengthMeters}
                  onChange={(e) => setNewYarn({ ...newYarn, skeinLengthMeters: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label htmlFor="yarn-hook">Recommended hook size (optional)</label>
                <input
                  id="yarn-hook"
                  placeholder="e.g. 4mm / US H-8"
                  value={newYarn.recommendedHookSize}
                  onChange={(e) => setNewYarn({ ...newYarn, recommendedHookSize: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label htmlFor="yarn-cost">Cost per skein (optional)</label>
                <input
                  id="yarn-cost"
                  type="number"
                  min={0}
                  step={0.01}
                  placeholder="what you paid"
                  value={newYarn.costPerSkein}
                  onChange={(e) => setNewYarn({ ...newYarn, costPerSkein: e.target.value })}
                />
              </div>
            </div>
            <div className="form-row">
              <label htmlFor="yarn-notes">Notes (optional)</label>
              <input id="yarn-notes" value={newYarn.notes} onChange={(e) => setNewYarn({ ...newYarn, notes: e.target.value })} />
            </div>
            <div className="toolbar">
              <button className="primary" type="submit">
                Add
              </button>
              <button type="button" onClick={() => { setShowNewYarn(false); setNewYarn(blankYarnDraft()); }}>
                Cancel
              </button>
            </div>
          </form>
        )}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>Who has what</h2>
        {loading ? (
          <p className="muted">Loading…</p>
        ) : yarnTypes.length === 0 ? (
          <p className="empty">No yarn types yet — add one above to start tracking inventory.</p>
        ) : (
          <div className="table-wrap">
            {/* Not .ot-table: its mobile card-collapse hides <thead> entirely, which
                works for a fixed-schema row (order #, status, ...) but breaks a
                person-per-column matrix like this one — collapsing it would turn each
                row into a stack of bare numbers with no member name attached to any of
                them. Scrolling horizontally on a narrow screen keeps the header row (and
                so each number's owner) intact instead. */}
            <table>
              <thead>
                <tr>
                  <th>Yarn</th>
                  {members.map((m) => (
                    <th key={m.id}>{m.id === user?.id ? "You" : m.name}</th>
                  ))}
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {yarnTypes.map((y) => {
                  const expanded = !!expandedYarn[y.id];
                  return (
                  <Fragment key={y.id}>
                  <tr>
                    <td className="cell-title">
                      <button
                        type="button"
                        className="iconbtn"
                        style={{ marginRight: 4 }}
                        aria-expanded={expanded}
                        aria-label={`${expanded ? "Hide" : "Show"} details for ${y.brand} ${y.colour}`}
                        onClick={() => setExpandedYarn({ ...expandedYarn, [y.id]: !expanded })}
                      >
                        {expanded ? "▾" : "▸"}
                      </button>
                      {y.brand} — {y.thickness}, {y.colour}
                    </td>
                    {members.map((m) => {
                      const isMe = m.id === user?.id;
                      const editing = editingQuantity[y.id] !== undefined;
                      return (
                        <td key={m.id} className="cell-order mono">
                          {isMe && editing ? (
                            <span style={{ display: "inline-flex", gap: 4 }}>
                              <input
                                aria-label={`Quantity for ${y.brand} ${y.colour}`}
                                type="number"
                                min={0}
                                step={0.25}
                                style={{ width: 64 }}
                                value={editingQuantity[y.id]}
                                onChange={(e) => setEditingQuantity({ ...editingQuantity, [y.id]: e.target.value })}
                              />
                              <button type="button" disabled={saving === y.id} onClick={() => saveQuantity(y.id)}>
                                Save
                              </button>
                            </span>
                          ) : isMe ? (
                            <button
                              type="button"
                              aria-label={`Edit your quantity of ${y.brand} ${y.colour}, currently ${quantityFor(y.id, m.id)}`}
                              onClick={() => startEditing(y.id)}
                            >
                              {quantityFor(y.id, m.id)}
                            </button>
                          ) : (
                            quantityFor(y.id, m.id)
                          )}
                          {isStale(y.id, m.id) && quantityFor(y.id, m.id) > 0 && (
                            <span
                              className="muted"
                              aria-label="Hasn't been updated in a while — this count might be out of date"
                              title="Hasn't been updated in a while — this count might be out of date"
                              style={{ fontSize: 11, marginLeft: 4 }}
                            >
                              stale
                            </span>
                          )}
                        </td>
                      );
                    })}
                    <td className="cell-type">
                      <button type="button" aria-label={`Remove yarn type ${y.brand} ${y.colour}`} onClick={() => removeYarnType(y.id)}>
                        Remove
                      </button>
                    </td>
                  </tr>
                  {expanded && (
                    <tr>
                      <td colSpan={members.length + 2} className="muted" style={{ fontSize: 12 }}>
                        {[y.material, y.skeinWeightGrams != null ? `${y.skeinWeightGrams}g` : null,
                          y.skeinLengthMeters != null ? `${y.skeinLengthMeters}m` : null,
                          y.recommendedHookSize ? `hook ${y.recommendedHookSize}` : null]
                          .filter(Boolean).join(" · ") || "No material details recorded."}
                        {y.notes && <div>{y.notes}</div>}
                        <div style={{ marginTop: 4 }}>
                          {editingCost[y.id] !== undefined ? (
                            <span style={{ display: "inline-flex", gap: 4, alignItems: "center" }}>
                              <input
                                aria-label={`Cost per skein for ${y.brand} ${y.colour}`}
                                type="number"
                                min={0}
                                step={0.01}
                                style={{ width: 72 }}
                                value={editingCost[y.id]}
                                onChange={(e) => setEditingCost({ ...editingCost, [y.id]: e.target.value })}
                              />
                              <button type="button" disabled={savingCost === y.id} onClick={() => saveCost(y)}>
                                Save
                              </button>
                            </span>
                          ) : (
                            <button
                              type="button"
                              onClick={() => setEditingCost({ ...editingCost, [y.id]: y.costPerSkein != null ? String(y.costPerSkein) : "" })}
                            >
                              {y.costPerSkein != null ? `${formatMoney(y.costPerSkein)}/skein` : "Set cost per skein"}
                            </button>
                          )}
                          {y.costHistory.length > 0 && (
                            <button type="button" style={{ marginLeft: 6 }} onClick={() => setShowCostHistory(showCostHistory === y.id ? null : y.id)}>
                              {showCostHistory === y.id ? "hide history" : "price history"}
                            </button>
                          )}
                        </div>
                        {showCostHistory === y.id && (
                          <ul style={{ margin: "4px 0 0", paddingLeft: 16 }}>
                            {y.costHistory.map((c, i) => (
                              <li key={i}>
                                {new Date(c.changedAt).toLocaleDateString()}: {c.previousCost != null ? formatMoney(c.previousCost) : "unset"} → {c.newCost != null ? formatMoney(c.newCost) : "unset"}
                              </li>
                            ))}
                          </ul>
                        )}
                      </td>
                    </tr>
                  )}
                  </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <p className="hint" style={{ marginTop: 10 }}>
          Quantities are in skeins, to the nearest quarter. Everyone in {currentInventoryGroup?.name} can see this
          whole table, but only your own column is editable.
        </p>
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <div className="toolbar">
          <h2 style={{ margin: 0 }}>Hooks &amp; needles</h2>
          <span className="spacer" />
          {!showNewNeedle && (
            <button type="button" onClick={() => setShowNewNeedle(true)}>
              + Add hook/needle
            </button>
          )}
        </div>

        {showNewNeedle && (
          <form onSubmit={submitNewNeedle} style={{ marginTop: 12 }}>
            <div className="form-grid">
              <div className="form-row">
                <label htmlFor="needle-kind">Kind</label>
                <select
                  id="needle-kind"
                  value={newNeedle.kind}
                  onChange={(e) => setNewNeedle({ ...newNeedle, kind: e.target.value as NeedleKind })}
                >
                  <option value="CROCHET_HOOK">Crochet hook</option>
                  <option value="KNITTING_NEEDLE">Knitting needle</option>
                </select>
              </div>
              <div className="form-row">
                <label htmlFor="needle-size">Size</label>
                <input
                  id="needle-size"
                  placeholder="e.g. 4mm / US H-8"
                  value={newNeedle.size}
                  onChange={(e) => setNewNeedle({ ...newNeedle, size: e.target.value })}
                  required
                />
              </div>
            </div>
            <div className="form-row">
              <label htmlFor="needle-notes">Notes (optional)</label>
              <input id="needle-notes" value={newNeedle.notes} onChange={(e) => setNewNeedle({ ...newNeedle, notes: e.target.value })} />
            </div>
            <div className="toolbar">
              <button className="primary" type="submit">
                Add
              </button>
              <button type="button" onClick={() => { setShowNewNeedle(false); setNewNeedle(blankNeedleDraft()); }}>
                Cancel
              </button>
            </div>
          </form>
        )}

        {loading ? (
          <p className="muted" style={{ marginTop: 12 }}>Loading…</p>
        ) : needleTypes.length === 0 ? (
          <p className="empty" style={{ marginTop: 12 }}>No hooks or needles yet — add one above to start tracking them.</p>
        ) : (
          <div className="table-wrap" style={{ marginTop: 12 }}>
            {/* See the yarn table above for why this isn't .ot-table: a person-per-column
                matrix needs its header row on mobile too, not the card collapse. */}
            <table>
              <thead>
                <tr>
                  <th>Hook / needle</th>
                  {members.map((m) => (
                    <th key={m.id}>{m.id === user?.id ? "You" : m.name}</th>
                  ))}
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {needleTypes.map((n) => (
                  <tr key={n.id}>
                    <td className="cell-title">
                      {n.kind === "CROCHET_HOOK" ? "Crochet hook" : "Knitting needle"} — {n.size}
                      {n.notes && <div className="muted" style={{ fontSize: 12 }}>{n.notes}</div>}
                    </td>
                    {members.map((m) => {
                      const isMe = m.id === user?.id;
                      const editing = editingNeedleQuantity[n.id] !== undefined;
                      return (
                        <td key={m.id} className="cell-order mono">
                          {isMe && editing ? (
                            <span style={{ display: "inline-flex", gap: 4 }}>
                              <input
                                aria-label={`Quantity for ${n.size}`}
                                type="number"
                                min={0}
                                step={1}
                                style={{ width: 56 }}
                                value={editingNeedleQuantity[n.id]}
                                onChange={(e) => setEditingNeedleQuantity({ ...editingNeedleQuantity, [n.id]: e.target.value })}
                              />
                              <button type="button" disabled={savingNeedle === n.id} onClick={() => saveNeedleQuantity(n.id)}>
                                Save
                              </button>
                            </span>
                          ) : isMe ? (
                            <button
                              type="button"
                              aria-label={`Edit your quantity of ${n.size}, currently ${needleQuantityFor(n.id, m.id)}`}
                              onClick={() => startEditingNeedle(n.id)}
                            >
                              {needleQuantityFor(n.id, m.id)}
                            </button>
                          ) : (
                            needleQuantityFor(n.id, m.id)
                          )}
                        </td>
                      );
                    })}
                    <td className="cell-type">
                      <button type="button" aria-label={`Remove ${n.size}`} onClick={() => removeNeedleType(n.id)}>
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="hint" style={{ marginTop: 10 }}>
          Whole units — no fractional hooks. Same visibility rule as yarn: everyone sees this table, only your own
          column is editable.
        </p>
      </div>
    </div>
  );
}
