import { useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { materialInventoryApi } from "../api";
import { useMaterialInventory } from "../MaterialInventoryContext";
import type { InventoryEntryView, YarnTypeView } from "../types";

type NewYarnDraft = { brand: string; thickness: string; colour: string; notes: string };

const blankYarnDraft = (): NewYarnDraft => ({ brand: "", thickness: "", colour: "", notes: "" });

/** Everyone's on-hand yarn, business-wide (design decision: full transparency, same
 *  precedent as Finance Tracker's balances) — this member's own row is editable inline,
 *  everyone else's is read-only. Yarn types themselves (brand + thickness + colour) are a
 *  shared, business-wide catalog any member can add to (design decision), so two people
 *  describing the same yarn always point at the same row instead of drifting apart. */
export default function MyInventoryPage() {
  const { currentInventoryGroup, currentGroupId } = useMaterialInventory();
  const { user } = useAuth();
  const [yarnTypes, setYarnTypes] = useState<YarnTypeView[]>([]);
  const [entries, setEntries] = useState<InventoryEntryView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showNewYarn, setShowNewYarn] = useState(false);
  const [newYarn, setNewYarn] = useState<NewYarnDraft>(blankYarnDraft());
  const [editingQuantity, setEditingQuantity] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState<string | null>(null);

  const members = currentInventoryGroup?.members ?? [];
  const memberName = (id: string) => members.find((m) => m.id === id)?.name ?? "Unknown";

  const load = () => {
    if (!currentGroupId) return;
    setLoading(true);
    Promise.all([materialInventoryApi.yarnTypes(currentGroupId), materialInventoryApi.inventory(currentGroupId)])
      .then(([types, inv]) => {
        setYarnTypes(types);
        setEntries(inv);
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  const quantityFor = (yarnTypeId: string, userId: string) =>
    entries.find((e) => e.yarnTypeId === yarnTypeId && e.userId === userId)?.quantity ?? 0;

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
        notes: newYarn.notes.trim() || null,
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

  return (
    <div>
      <h1 className="page-title">Team inventory</h1>
      <p className="page-sub">{currentInventoryGroup?.name}</p>
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
            <table className="ot-table">
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
                {yarnTypes.map((y) => (
                  <tr key={y.id}>
                    <td className="cell-title">
                      {y.brand} — {y.thickness}, {y.colour}
                      {y.notes && <div className="muted" style={{ fontSize: 12 }}>{y.notes}</div>}
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
                            <button type="button" onClick={() => startEditing(y.id)}>
                              {quantityFor(y.id, m.id)}
                            </button>
                          ) : (
                            quantityFor(y.id, m.id)
                          )}
                        </td>
                      );
                    })}
                    <td className="cell-type">
                      <button type="button" onClick={() => removeYarnType(y.id)}>
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
          Quantities are in skeins, to the nearest quarter. Everyone in {currentInventoryGroup?.name} can see this
          whole table, but only your own column is editable.
        </p>
      </div>
    </div>
  );
}
