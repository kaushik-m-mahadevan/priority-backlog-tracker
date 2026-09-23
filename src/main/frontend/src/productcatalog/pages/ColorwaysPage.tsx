import { useEffect, useState } from "react";
import { productCatalogApi } from "../api";
import { useProductCatalog } from "../ProductCatalogContext";
import { formatMoney } from "../../lib/format";
import Connections from "../../components/Connections";
import ImageGallery from "../../components/ImageGallery";
import type { ColorwayView } from "../types";

type NewColorwayDraft = {
  name: string;
  colour: string;
  estimatedCost: string;
  notes: string;
  recipeSteps: string;
  referenceLink: string;
};

const blankDraft = (): NewColorwayDraft => ({
  name: "", colour: "", estimatedCost: "", notes: "", recipeSteps: "", referenceLink: "",
});

const toRecipeSteps = (text: string): string[] | null => {
  const steps = text.split("\n").map((s) => s.trim()).filter(Boolean);
  return steps.length > 0 ? steps : null;
};

/** The fields shared between the "new colorway" and "edit colorway" forms — previously
 *  declared twice. Renders just the fields, not the surrounding form/buttons, since the
 *  two call sites wrap them differently (a real <form> for create, plain buttons for
 *  inline edit). A reference link and an uploaded photo are independent (ui-12: both,
 *  not either/or) — the link is a plain text field here; the photo gallery only appears
 *  once the colorway has an id, so it lives on the card itself, not in this shared form. */
function ColorwayForm({
  idPrefix,
  draft,
  onChange,
}: {
  idPrefix: string;
  draft: NewColorwayDraft;
  onChange: (next: NewColorwayDraft) => void;
}) {
  const set = <K extends keyof NewColorwayDraft>(key: K, value: NewColorwayDraft[K]) =>
    onChange({ ...draft, [key]: value });

  return (
    <>
      <div className="form-grid">
        <div className="form-row">
          <label htmlFor={`${idPrefix}-name`}>Name</label>
          <input id={`${idPrefix}-name`} value={draft.name} onChange={(e) => set("name", e.target.value)} required />
        </div>
        <div className="form-row">
          <label htmlFor={`${idPrefix}-colour`}>Colour</label>
          <input id={`${idPrefix}-colour`} value={draft.colour} onChange={(e) => set("colour", e.target.value)} required />
        </div>
        <div className="form-row">
          <label htmlFor={`${idPrefix}-cost`}>Estimated cost (optional)</label>
          <input
            id={`${idPrefix}-cost`}
            type="number"
            min={0}
            step={0.01}
            value={draft.estimatedCost}
            onChange={(e) => set("estimatedCost", e.target.value)}
          />
        </div>
      </div>
      <div className="form-row">
        <label htmlFor={`${idPrefix}-notes`}>Notes (optional)</label>
        <input id={`${idPrefix}-notes`} value={draft.notes} onChange={(e) => set("notes", e.target.value)} />
      </div>
      <div className="form-row">
        <label htmlFor={`${idPrefix}-link`}>Reference link (optional)</label>
        <input
          id={`${idPrefix}-link`}
          type="url"
          placeholder="e.g. a Ravelry or Etsy pattern page"
          value={draft.referenceLink}
          onChange={(e) => set("referenceLink", e.target.value)}
        />
      </div>
      <div className="form-row">
        <label htmlFor={`${idPrefix}-recipe`}>Recipe steps (optional, one per line)</label>
        <textarea
          id={`${idPrefix}-recipe`}
          rows={4}
          value={draft.recipeSteps}
          onChange={(e) => set("recipeSteps", e.target.value)}
        />
      </div>
    </>
  );
}

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
  /** Which section triggered "+ New colorway" — determines whether the freshly-created
   *  colorway is immediately promoted out of the idea box (design decision: reuse the
   *  existing create+promote endpoints rather than adding a backend "create directly in
   *  catalog" flag, since promote() is already a simple no-side-effect flip). */
  const [newTarget, setNewTarget] = useState<"idea" | "catalog">("idea");
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
      const created = await productCatalogApi.createColorway(currentGroupId, {
        name: draft.name.trim(),
        colour: draft.colour.trim(),
        estimatedCost: draft.estimatedCost.trim() ? Number(draft.estimatedCost) : null,
        notes: draft.notes.trim() || null,
        recipeSteps: toRecipeSteps(draft.recipeSteps),
        referenceLink: draft.referenceLink.trim() || null,
      });
      if (newTarget === "catalog") {
        await productCatalogApi.promoteColorway(currentGroupId, created.id);
      }
      setDraft(blankDraft());
      setShowNew(false);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add colorway");
    }
  };

  const openNewForm = (target: "idea" | "catalog") => {
    setNewTarget(target);
    setDraft(blankDraft());
    setShowNew(true);
  };

  const startEditing = (c: ColorwayView) => {
    setEditingId(c.id);
    setEditDraft({
      name: c.name,
      colour: c.colour,
      estimatedCost: c.estimatedCost != null ? String(c.estimatedCost) : "",
      notes: c.notes ?? "",
      recipeSteps: (c.pattern?.recipeSteps ?? []).join("\n"),
      referenceLink: c.pattern?.referenceLink ?? "",
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
        referenceLink: editDraft.referenceLink.trim() || null,
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
          <ColorwayForm idPrefix={`edit-${c.id}`} draft={editDraft} onChange={setEditDraft} />
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
            {c.estimatedCost != null && <span className="muted"> · est. {formatMoney(c.estimatedCost)}</span>}
          </div>
          <span className="spacer" />
          {c.ideabox && (
            <button type="button" aria-label={`Promote ${c.name} to catalog`} onClick={() => promote(c.id)}>
              Promote to catalog
            </button>
          )}
          <button type="button" aria-label={`Edit ${c.name}`} onClick={() => startEditing(c)}>
            Edit
          </button>
          <button type="button" aria-label={`Remove ${c.name}`} onClick={() => remove(c.id)}>
            Remove
          </button>
        </div>
        {c.notes && <p className="muted" style={{ fontSize: 12 }}>{c.notes}</p>}
        {c.pattern?.referenceLink && (
          <p style={{ fontSize: 13, marginTop: 4 }}>
            <a href={c.pattern.referenceLink} target="_blank" rel="noreferrer">
              {c.pattern.referenceLink}
            </a>
          </p>
        )}
        {c.pattern && c.pattern.recipeSteps.length > 0 && (
          <ol style={{ marginTop: 8, paddingLeft: 20 }}>
            {c.pattern.recipeSteps.map((step, i) => (
              <li key={i}>{step}</li>
            ))}
          </ol>
        )}
        {/* Photo is independent of the link above (ui-12: both, not either/or) — the
            generic gallery already used by Order Tracker's finished-product photos. */}
        {currentGroupId && (
          <div style={{ marginTop: 10 }}>
            <ImageGallery groupId={currentGroupId} ownerType="colorway" ownerId={c.id} />
          </div>
        )}
      </div>
    );
  };

  const newForm = (
    <form onSubmit={submitNew} style={{ marginTop: 12 }}>
      <ColorwayForm idPrefix="new" draft={draft} onChange={setDraft} />
      <div className="toolbar">
        <button className="primary" type="submit">
          Add
        </button>
        <button type="button" onClick={() => { setShowNew(false); setDraft(blankDraft()); }}>
          Cancel
        </button>
      </div>
    </form>
  );

  return (
    <div>
      <h1 className="page-title">Colorways</h1>
      <div className="toolbar" style={{ marginBottom: 8 }}>
        <p className="page-sub" style={{ margin: 0 }}>{currentCatalogGroup?.name}</p>
        <span className="spacer" />
        <Connections appletKey="productcatalog" groupId={currentGroupId} />
      </div>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <div className="toolbar">
          <h2 style={{ margin: 0 }}>Idea box</h2>
          <span className="spacer" />
          {!showNew && (
            <button type="button" onClick={() => openNewForm("idea")}>
              + New colorway
            </button>
          )}
        </div>

        {showNew && newTarget === "idea" && newForm}

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
        <div className="toolbar">
          <h2 style={{ margin: 0 }}>Catalog</h2>
          <span className="spacer" />
          {!showNew && (
            <button type="button" onClick={() => openNewForm("catalog")}>
              + New colorway
            </button>
          )}
        </div>

        {showNew && newTarget === "catalog" && newForm}

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
