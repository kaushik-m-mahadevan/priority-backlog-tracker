import { FormEvent, useState } from "react";
import { api, ApiError } from "../api/client";
import { useConfig } from "../config/ConfigContext";
import { useUsers } from "../users/UsersContext";
import { notifyItemsChanged } from "../lib/events";
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

const UNIT_RULES: Record<
  Unit,
  { min: number; max: number; step: number; hint: string; ok: (n: number) => boolean; err: string }
> = {
  MINUTES: {
    min: 15,
    max: 45,
    step: 15,
    hint: "15, 30 or 45",
    ok: (n) => [15, 30, 45].includes(n),
    err: "Minutes must be 15, 30, or 45",
  },
  HOURS: {
    min: 1,
    max: 23,
    step: 1,
    hint: "1–23 whole hours",
    ok: (n) => Number.isInteger(n) && n >= 1 && n <= 23,
    err: "Hours must be a whole number from 1 to 23",
  },
  DAYS: {
    min: 1,
    max: 30,
    step: 1,
    hint: "1–30 whole days",
    ok: (n) => Number.isInteger(n) && n >= 1 && n <= 30,
    err: "Days must be a whole number from 1 to 30",
  },
};

export default function ItemFormModal({ existing, onClose, onSaved, onComplete }: Props) {
  const config = useConfig();
  const { users, nameOf } = useUsers();
  const editing = !!existing;

  const plus30 = () => {
    const d = new Date();
    d.setDate(d.getDate() + 30);
    return d.toISOString().slice(0, 10);
  };

  const init = {
    title: existing?.title ?? "",
    category: existing?.category ?? "",
    priority: existing?.priority ?? "",
    unit: (existing?.effort?.unit ?? "MINUTES") as Unit,
    value: String(existing?.effort?.value ?? 30),
    dueDate: existing?.dueDate ? existing.dueDate.slice(0, 10) : plus30(),
    ownerId: existing?.ownerId ?? "",
    notes: existing?.notes?.content ?? "",
  };

  const [title, setTitle] = useState(init.title);
  const [category, setCategory] = useState(init.category);
  const [priority, setPriority] = useState(init.priority);
  const [unit, setUnit] = useState<Unit>(init.unit);
  const [value, setValue] = useState<string>(init.value);
  const [dueDate, setDueDate] = useState(init.dueDate);
  const [ownerId, setOwnerId] = useState(init.ownerId);
  const [notes, setNotes] = useState(init.notes);
  const [status, setStatus] = useState(existing?.status ?? "BACKLOG");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusFlash, setStatusFlash] = useState(false);

  const dirty =
    title !== init.title ||
    category !== init.category ||
    priority !== init.priority ||
    unit !== init.unit ||
    value !== init.value ||
    dueDate !== init.dueDate ||
    ownerId !== init.ownerId ||
    notes !== init.notes;

  function requestClose() {
    if (dirty && !window.confirm("Discard your unsaved changes?")) return;
    onClose();
  }

  async function toggleStatus() {
    if (!existing) return;
    const next = status === "BACKLOG" ? "IN_PROGRESS" : "BACKLOG";
    setBusy(true);
    setError(null);
    try {
      await api.patch(`/items/${existing.id}/status`, { status: next });
      setStatus(next);
      setStatusFlash(true);
      setTimeout(() => setStatusFlash(false), 1400);
      notifyItemsChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not change status");
    } finally {
      setBusy(false);
    }
  }

  const effortNum = Number(value);
  const effortInvalid = value.trim() === "" || !UNIT_RULES[unit].ok(effortNum);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (effortInvalid) {
      setError(UNIT_RULES[unit].err);
      return;
    }
    setBusy(true);
    setError(null);
    const body: Record<string, unknown> = {
      title,
      category,
      priority,
      effortEstimate: { value: effortNum, unit },
      ownerId: ownerId || null,
      notes,
      version: existing?.version ?? null,
    };
    if (dueDate) body.dueDate = new Date(dueDate + "T00:00:00Z").toISOString();
    try {
      if (editing) await api.put(`/items/${existing!.id}`, body);
      else await api.post("/items", body);
      notifyItemsChanged();
      onSaved();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError(
          "Someone else changed this item since you opened it — your edits weren't saved. " +
            "Close and reopen to get the latest.",
        );
      } else {
        setError(err instanceof ApiError ? err.message : "Save failed");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={requestClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{editing ? existing!.title || existing!.itemId : "New item"}</h3>
          {editing && (
            <button type="button" className="ghost" onClick={toggleStatus} disabled={busy}>
              {statusFlash
                ? "Saved ✓"
                : status === "IN_PROGRESS"
                  ? "Move to backlog"
                  : "Start"}
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

          <div className="form-row">
            <label>Effort</label>
            <div className="effort-input">
              <input
                type="number"
                inputMode="numeric"
                value={value}
                min={UNIT_RULES[unit].min}
                max={UNIT_RULES[unit].max}
                step={UNIT_RULES[unit].step}
                aria-invalid={effortInvalid}
                onChange={(e) => setValue(e.target.value)}
              />
              <select
                value={unit}
                onChange={(e) => {
                  const u = e.target.value as Unit;
                  setUnit(u);
                  if (!UNIT_RULES[u].ok(Number(value))) setValue(String(UNIT_RULES[u].min));
                }}
              >
                <option value="MINUTES">min</option>
                <option value="HOURS">hr</option>
                <option value="DAYS">days</option>
              </select>
            </div>
            <div className={`hint${effortInvalid ? " bad" : ""}`}>
              {effortInvalid ? UNIT_RULES[unit].err : UNIT_RULES[unit].hint}
            </div>
          </div>

          <div className="form-grid">
            <div className="form-row">
              <label>Due date</label>
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
            {dirty && <span className="unsaved">Unsaved changes</span>}
            <button type="button" className="ghost" onClick={requestClose} disabled={busy}>
              {dirty ? "Discard" : "Close"}
            </button>
            <button
              type="submit"
              className="primary"
              disabled={busy || effortInvalid || (editing && !dirty)}
            >
              {busy ? "Saving…" : editing ? "Save changes" : "Create item"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
