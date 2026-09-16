import { useEffect, useState } from "react";
import { productCatalogApi } from "../api";
import { useProductCatalog } from "../ProductCatalogContext";
import type { ColorwayView } from "../types";

type NewColorwayDraft = {
  name: string;
  colour: string;
  estimatedCost: string;
  notes: string;
  recipeSteps: string;
};

const blankDraft = (): NewColorwayDraft => ({ name: "", colour: "", estimatedCost: "", notes: "", recipeSteps: "" });

const toRecipeSteps = (text: string): string[] | null => {
  const steps = text.split("\n").map((s) => s.trim()).filter(Boolean);
  return steps.length > 0 ? steps : null;
};

/** Idea box vs. catalog (design decision: the ideabox flag is a simple flip, no other
 *  side effects) — any business member can add or edit a colorway (same precedent as
 *  Material Inventory's yarn types). v1 keeps the create/edit form to name, colour,
 *  estimated cost, notes, and a plain recipe-steps textarea; patternType/templateName/
 *  attachmentUrls have no UI yet (design decision: defer to a later pass). */
export default function ColorwaysPage() {
  const { currentCatalogGroup, currentGroupId } = useProductCatalog();
  const [colorways, setColorways] = useState<ColorwayView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showNew, setShowNew] = useState(false);
  const [draft, setDraft] = useState<NewColorwayDraft>(blankDraft());
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState<NewColorwayDraft>(blankDraft());

  const load = () => {
    if (!currentGroupId) return;
    setLoading(true);
    productCatalogApi.colorways(currentGroupId)
      .then(setColorways)
      .finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  const submitNew = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentGroupId) return;
    if (!draft.name.trim() || !draft.colour.trim()) {
      setError("Name and colour are both required");
      return;
    }
    setError(null);
    try {
      await productCatalogApi.createColorway(currentGroupId, {
        name: draft.name.trim(),
        colour: draft.colour.trim(),
        estimatedCost: draft.estimatedCost.trim() ? Number(draft.estimatedCost) : null,
        notes: draft.notes.trim() || null,
        recipeSteps: toRecipeSteps(draft.recipeSteps),
      });
      setDraft(blankDraft());
      setShowNew(false);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add colorway");
    }
  };

  const startEditing = (c: ColorwayView) => {
    setEditingId(c.id);
    setEditDraft({
      name: c.name,
      colour: c.colour,
      estimatedCost: c.estimatedCost != null ? String(c.estimatedCost) : "",
      notes: c.notes ?? "",
      recipeSteps: (c.pattern?.recipeSteps ?? []).join("\n"),
    });
  };

  const saveEdit = async (colorwayId: string) => {
    if (!currentGroupId) return;
    if (!editDraft.name.trim() || !editDraft.colour.trim()) {
      setError("Name and colour are both required");
      return;
    }
    setError(null);
    try {
      await productCatalogApi.updateColorway(currentGroupId, colorwayId, {
        name: editDraft.name.trim(),
        colour: editDraft.colour.trim(),
        estimatedCost: editDraft.estimatedCost.trim() ? Number(editDraft.estimatedCost) : null,
        notes: editDraft.notes.trim() || null,
        recipeSteps: toRecipeSteps(editDraft.recipeSteps),
      });
      setEditingId(null);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update colorway");
    }
  };

  const promote = async (colorwayId: string) => {
    if (!currentGroupId) return;
    setError(null);
    try {
      await productCatalogApi.promoteColorway(currentGroupId, colorwayId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to promote colorway");
    }
  };

  const remove = async (colorwayId: string) => {
    if (!currentGroupId) return;
    setError(null);
    try {
      await productCatalogApi.deleteColorway(currentGroupId, colorwayId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to remove colorway");
    }
  };

  const ideabox = colorways.filter((c) => c.ideabox);
  const catalog = colorways.filter((c) => !c.ideabox);

  const renderCard = (c: ColorwayView) => {
    const isEditing = editingId === c.id;
    if (isEditing) {
      return (
        <div className="card" key={c.id}>
          <div className="form-grid">
            <div className="form-row">
              <label htmlFor={`edit-name-${c.id}`}>Name</label>
              <input id={`edit-name-${c.id}`} value={editDraft.name} onChange={(e) => setEditDraft({ ...editDraft, name: e.target.value })} required />
            </div>
            <div className="form-row">
              <label htmlFor={`edit-colour-${c.id}`}>Colour</label>
              <input id={`edit-colour-${c.id}`} value={editDraft.colour} onChange={(e) => setEditDraft({ ...editDraft, colour: e.target.value })} required />
            </div>
            <div className="form-row">
              <label htmlFor={`edit-cost-${c.id}`}>Estimated cost (optional)</label>
              <input
                id={`edit-cost-${c.id}`}
                type="number"
                min={0}
                step={0.01}
                value={editDraft.estimatedCost}
                onChange={(e) => setEditDraft({ ...editDraft, estimatedCost: e.target.value })}
              />
            </div>
          </div>
          <div className="form-row">
            <label htmlFor={`edit-notes-${c.id}`}>Notes (optional)</label>
            <input id={`edit-notes-${c.id}`} value={editDraft.notes} onChange={(e) => setEditDraft({ ...editDraft, notes: e.target.value })} />
          </div>
          <div className="form-row">
            <label htmlFor={`edit-recipe-${c.id}`}>Recipe steps (optional, one per line)</label>
            <textarea
              id={`edit-recipe-${c.id}`}
              rows={4}
              value={editDraft.recipeSteps}
              onChange={(e) => setEditDraft({ ...editDraft, recipeSteps: e.target.value })}
            />
          </div>
          <div className="toolbar">
            <button className="primary" type="button" onClick={() => saveEdit(c.id)}>
              Save
            </button>
            <button type="button" onClick={() => setEditingId(null)}>
              Cancel
            </button>
          </div>
        </div>
      );
    }

    return (
      <div className="card" key={c.id}>
        <div className="toolbar">
          <div>
            <strong>{c.name}</strong> — {c.colour}
            {c.estimatedCost != null && <span className="muted"> · est. ${c.estimatedCost.toFixed(2)}</span>}
          </div>
          <span className="spacer" />
          {c.ideabox && (
            <button type="button" onClick={() => promote(c.id)}>
              Promote to catalog
            </button>
          )}
          <button type="button" onClick={() => startEditing(c)}>
            Edit
          </button>
          <button type="button" onClick={() => remove(c.id)}>
            Remove
          </button>
        </div>
        {c.notes && <p className="muted" style={{ fontSize: 12 }}>{c.notes}</p>}
        {c.pattern && c.pattern.recipeSteps.length > 0 && (
          <ol style={{ marginTop: 8, paddingLeft: 20 }}>
            {c.pattern.recipeSteps.map((step, i) => (
              <li key={i}>{step}</li>
            ))}
          </ol>
        )}
      </div>
    );
  };

  return (
    <div>
      <h1 className="page-title">Colorways</h1>
      <p className="page-sub">{currentCatalogGroup?.name}</p>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <div className="toolbar">
          <h2 style={{ margin: 0 }}>Idea box</h2>
          <span className="spacer" />
          {!showNew && (
            <button type="button" onClick={() => setShowNew(true)}>
              + New colorway
            </button>
          )}
        </div>

        {showNew && (
          <form onSubmit={submitNew} style={{ marginTop: 12 }}>
            <div className="form-grid">
              <div className="form-row">
                <label htmlFor="new-name">Name</label>
                <input id="new-name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} required />
              </div>
              <div className="form-row">
                <label htmlFor="new-colour">Colour</label>
                <input id="new-colour" value={draft.colour} onChange={(e) => setDraft({ ...draft, colour: e.target.value })} required />
              </div>
              <div className="form-row">
                <label htmlFor="new-cost">Estimated cost (optional)</label>
                <input
                  id="new-cost"
                  type="number"
                  min={0}
                  step={0.01}
                  value={draft.estimatedCost}
                  onChange={(e) => setDraft({ ...draft, estimatedCost: e.target.value })}
                />
              </div>
            </div>
            <div className="form-row">
              <label htmlFor="new-notes">Notes (optional)</label>
              <input id="new-notes" value={draft.notes} onChange={(e) => setDraft({ ...draft, notes: e.target.value })} />
            </div>
            <div className="form-row">
              <label htmlFor="new-recipe">Recipe steps (optional, one per line)</label>
              <textarea
                id="new-recipe"
                rows={4}
                value={draft.recipeSteps}
                onChange={(e) => setDraft({ ...draft, recipeSteps: e.target.value })}
              />
            </div>
            <div className="toolbar">
              <button className="primary" type="submit">
                Add
              </button>
              <button type="button" onClick={() => { setShowNew(false); setDraft(blankDraft()); }}>
                Cancel
              </button>
            </div>
          </form>
        )}

        {loading ? (
          <p className="muted">Loading…</p>
        ) : ideabox.length === 0 ? (
          <p className="empty">No ideas yet — add one above.</p>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 12, marginTop: 12 }}>
            {ideabox.map(renderCard)}
          </div>
        )}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>Catalog</h2>
        {loading ? (
          <p className="muted">Loading…</p>
        ) : catalog.length === 0 ? (
          <p className="empty">Nothing promoted to the catalog yet.</p>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            {catalog.map(renderCard)}
          </div>
        )}
      </div>
    </div>
  );
}
