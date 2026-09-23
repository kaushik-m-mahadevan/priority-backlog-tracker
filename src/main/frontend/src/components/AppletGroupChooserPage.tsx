import { FormEvent, useState } from "react";
import { ApiError } from "../api/client";
import type { GroupView } from "../types";

/** ui-1: the applet-scoped group/business picker, relocated out of the header (which was
 *  overflowing at mobile widths — see BACKLOG.md "Header overflow") into its own screen.
 *  One generic component shared by every applet's own group concept (Order Tracker's
 *  "business", Finance/Inventory/Catalog's "group") rather than 4 near-identical copies,
 *  since — like the Context layer these read from (fdup-4's createAppletGroupContext) —
 *  the picking/creating logic itself is identical, only the field names on each applet's
 *  own context differ, which is why this takes plain props instead of a context directly. */
export default function AppletGroupChooserPage({
  icon,
  groupNoun,
  description,
  groups,
  currentGroupId,
  loading,
  onSelect,
  onCreate,
}: {
  icon: string;
  /** e.g. "business", "finance group", "inventory group", "catalog group" — used in copy. */
  groupNoun: string;
  description: string;
  groups: GroupView[];
  currentGroupId: string | null;
  loading: boolean;
  onSelect: (id: string) => void;
  onCreate: (name: string) => Promise<unknown>;
}) {
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await onCreate(name.trim());
      setName("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : `Could not create the ${groupNoun}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="launcher" style={{ padding: "24px 0" }}>
      <div className="launcher-head">
        <h1 className="page-title">Choose a {groupNoun}</h1>
        <p className="page-sub">{description}</p>
      </div>

      {error && <div className="error">{error}</div>}

      {loading ? (
        <p className="muted">Loading…</p>
      ) : (
        <div className="applet-grid" style={{ marginBottom: 24 }}>
          {groups.map((g) => (
            <button
              key={g.id}
              type="button"
              className="applet-card"
              style={{ border: g.id === currentGroupId ? "1px solid var(--accent)" : undefined, cursor: "pointer", width: "100%" }}
              onClick={() => onSelect(g.id)}
            >
              <span className="applet-icon" aria-hidden="true">
                {icon}
              </span>
              <h2>{g.name}</h2>
              <p>
                {g.members.length} member{g.members.length === 1 ? "" : "s"}
                {g.id === currentGroupId && " · current"}
              </p>
            </button>
          ))}
          {groups.length === 0 && <p className="empty">No {groupNoun}s yet — create your first one below.</p>}
        </div>
      )}

      <div className="card">
        <h2>New {groupNoun}</h2>
        <form className="team-add" onSubmit={create}>
          <input
            aria-label={`${groupNoun} name`}
            placeholder={`${groupNoun[0].toUpperCase()}${groupNoun.slice(1)} name`}
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={60}
          />
          <button className="primary" disabled={busy || !name.trim()}>
            Create
          </button>
        </form>
      </div>
    </div>
  );
}
