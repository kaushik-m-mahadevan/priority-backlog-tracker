import { FormEvent, useState } from "react";
import { api, ApiError } from "../api/client";
import { useConfig } from "../config/ConfigContext";
import { useUsers } from "../users/UsersContext";
import { formatDateTime } from "../lib/format";
import MarkdownField from "./MarkdownField";
import type { Item } from "../types";

interface Props {
  existing?: Item | null;
  onClose: () => void;
  onSaved: () => void;
  /** when provided, shows Mark-as controls; the parent runs the completion + animation */
  onComplete?: (terminalStatus: string) => void;
}

type Unit = "MINUTES" | "HOURS" | "DAYS";

const EFFORT_OPTIONS: Record<Unit, number[]> = {
  MINUTES: [15, 30, 45],
  HOURS: Array.from({ length: 23 }, (_, i) => i + 1),
  DAYS: Array.from({ length: 30 }, (_, i) => i + 1),
};

export default function ItemFormModal({ existing, onClose, onSaved, onComplete }: Props) {
  const config = useConfig();
  const { users, nameOf } = useUsers();
  const editing = !!existing;

  const [title, setTitle] = useState(existing?.title ?? "");
  const [category, setCategory] = useState(existing?.category ?? "");
  const [priority, setPriority] = useState(existing?.priority ?? "");
  const [unit, setUnit] = useState<Unit>(existing?.effort?.unit ?? "MINUTES");
  const [value, setValue] = useState<number>(existing?.effort?.value ?? 30);
  const [dueDate, setDueDate] = useState(existing?.dueDate ? existing.dueDate.slice(0, 10) : "");
  const [ownerId, setOwnerId] = useState(existing?.ownerId ?? "");
  const [notes, setNotes] = useState(existing?.notes?.content ?? "");
  const [status, setStatus] = useState(existing?.status ?? "BACKLOG");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function toggleStatus() {
    if (!existing) return;
    const next = status === "BACKLOG" ? "IN_PROGRESS" : "BACKLOG";
    setBusy(true);
    setError(null);
    try {
      await api.patch(`/items/${existing.id}/status`, { status: next });
      setStatus(next);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not change status");
    } finally {
      setBusy(false);
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const body: Record<string, unknown> = {
      title,
      category,
      priority,
      effortEstimate: { value: Number(value), unit },
      ownerId: ownerId || null,
      notes,
    };
    if (dueDate) body.dueDate = new Date(dueDate + "T00:00:00Z").toISOString();
    try {
      if (editing) await api.put(`/items/${existing!.id}`, body);
      else await api.post("/items", body);
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
    } finally {
      setBusy(false);
    }
  }

  const options = EFFORT_OPTIONS[unit] ?? [];

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{editing ? existing!.title || existing!.itemId : "New item"}</h3>
          {editing && (
            <button type="button" className="ghost" onClick={toggleStatus} disabled={busy}>
              {status === "IN_PROGRESS" ? "Move to backlog" : "Start"}
            </button>
          )}
        </div>
        {error && <div className="error">{error}</div>}

        <form onSubmit={submit}>
          <div className="form-row">
            <label>Title</label>
            <input value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
          </div>

          <div className="form-grid">
            <div className="form-row">
              <label>Category</label>
              <select value={category} onChange={(e) => setCategory(e.target.value)} required>
                <option value="" disabled>
                  Select…
                </option>
                {config?.categories.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-row">
              <label>Priority</label>
              <select value={priority} onChange={(e) => setPriority(e.target.value)} required>
                <option value="" disabled>
                  Select…
                </option>
                {config?.priorities.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="form-grid">
            <div className="form-row">
              <label>Effort unit</label>
              <select
                value={unit}
                onChange={(e) => {
                  const u = e.target.value as Unit;
                  setUnit(u);
                  setValue(EFFORT_OPTIONS[u][0]);
                }}
              >
                <option value="MINUTES">Minutes</option>
                <option value="HOURS">Hours</option>
                <option value="DAYS">Days</option>
              </select>
            </div>
            <div className="form-row">
              <label>Effort amount</label>
              <select value={value} onChange={(e) => setValue(Number(e.target.value))}>
                {options.map((o) => (
                  <option key={o} value={o}>
                    {o}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="form-grid">
            <div className="form-row">
              <label>Due date {editing ? "" : "(optional — defaults to +30 days)"}</label>
              <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
            </div>
            <div className="form-row">
              <label>Assignee</label>
              <select value={ownerId} onChange={(e) => setOwnerId(e.target.value)}>
                <option value="">Unassigned</option>
                {users.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="form-row">
            <label>Notes</label>
            <MarkdownField
              value={notes}
              onChange={setNotes}
              placeholder="Context, links, next step… **bold**, _italic_, ## heading, - list"
            />
          </div>

          {editing && (
            <div className="detail-facts">
              <span>Status</span>
              <span>{status === "IN_PROGRESS" ? "In progress" : "Backlog"}</span>
              <span>Created</span>
              <span>
                {formatDateTime(existing!.createdAt)}
                {existing!.createdBy && ` · ${nameOf(existing!.createdBy)}`}
              </span>
              <span>Updated</span>
              <span>
                {formatDateTime(existing!.updatedAt)}
                {existing!.lastUpdatedBy && ` · ${nameOf(existing!.lastUpdatedBy)}`}
              </span>
            </div>
          )}

          {editing && onComplete && (
            <div className="mark-as">
              <span>Mark as</span>
              {(["RESOLVED", "REJECTED", "ARCHIVED"] as const).map((t) => (
                <button key={t} type="button" onClick={() => onComplete(t)} disabled={busy}>
                  {t[0] + t.slice(1).toLowerCase()}
                </button>
              ))}
            </div>
          )}

          <div className="modal-actions">
            <button type="button" className="ghost" onClick={onClose} disabled={busy}>
              Cancel
            </button>
            <button type="submit" className="primary" disabled={busy}>
              {busy ? "Saving…" : editing ? "Save changes" : "Create item"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
