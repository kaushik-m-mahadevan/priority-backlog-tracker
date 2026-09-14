import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { backlogLinkApi, type BacklogGroup, type BacklogItemView } from "./backlogLink";
import type { OrderView } from "./types";

type Unit = "MINUTES" | "HOURS" | "DAYS";

const UNIT_RULES: Record<Unit, { hint: string; ok: (n: number) => boolean }> = {
  MINUTES: { hint: "15, 30 or 45", ok: (n) => [15, 30, 45].includes(n) },
  HOURS: { hint: "1–23 whole hours", ok: (n) => Number.isInteger(n) && n >= 1 && n <= 23 },
  DAYS: { hint: "1–30 whole days", ok: (n) => Number.isInteger(n) && n >= 1 && n <= 30 },
};

function dueDateFor(order: OrderView): string {
  const due =
    order.orderType === "INDIVIDUAL" ? order.costEstimate?.computedDueDate : order.bulkDetails?.computedDueDate;
  return (due ?? order.quotedDeliveryDate ?? "").slice(0, 10);
}

export default function AddToGroupModal({ order, onClose }: { order: OrderView; onClose: () => void }) {
  const [groups, setGroups] = useState<BacklogGroup[]>([]);
  const [groupId, setGroupId] = useState("");
  const [checking, setChecking] = useState(false);
  const [existingItem, setExistingItem] = useState<BacklogItemView | null>(null);

  const [categories, setCategories] = useState<string[]>([]);
  const [priorities, setPriorities] = useState<string[]>([]);

  const [title, setTitle] = useState(order.itemName ?? "");
  const [category, setCategory] = useState("");
  const [priority, setPriority] = useState("");
  const [unit, setUnit] = useState<Unit>("DAYS");
  const [value, setValue] = useState("3");
  const [dueDate, setDueDate] = useState(dueDateFor(order));
  const [ownerId, setOwnerId] = useState("");
  const [notes, setNotes] = useState(`Linked from Order Tracker order ${order.orderNumber}.`);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<BacklogItemView | null>(null);

  useEffect(() => {
    backlogLinkApi.myGroups().then(setGroups);
  }, []);

  useEffect(() => {
    if (!groupId) {
      setExistingItem(null);
      return;
    }
    setChecking(true);
    setExistingItem(null);
    setError(null);
    Promise.all([
      backlogLinkApi.findLinkedItem(groupId, order.id),
      backlogLinkApi.categories(groupId),
      backlogLinkApi.priorities(),
    ])
      .then(([existing, cats, cfg]) => {
        setExistingItem(existing);
        setCategories(cats.categories);
        setPriorities(cfg.priorities);
        if (!category && cats.categories.length > 0) setCategory(cats.categories[0]);
        if (!priority && cfg.priorities.length > 0) setPriority(cfg.priorities[0]);
      })
      .catch(() =>
        setError("Couldn't check whether this order is already linked to a backlog item — try again.")
      )
      .finally(() => setChecking(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId]);

  const group = groups.find((g) => g.id === groupId);

  const effortInvalid = !UNIT_RULES[unit].ok(Number(value));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!groupId || !title.trim() || !category || !priority) return;
    if (effortInvalid) {
      setError(`Effort must be ${UNIT_RULES[unit].hint}`);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const item = await backlogLinkApi.createLinkedItem({
        groupId,
        title: title.trim(),
        category,
        priority,
        effortEstimate: { value: Number(value), unit },
        dueDate: dueDate ? new Date(dueDate + "T00:00:00Z").toISOString() : null,
        ownerId: ownerId || null,
        notes,
        linkedOrderId: order.id,
      });
      setCreated(item);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add to group");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className="modal-backdrop"
      onClick={onClose}
      onKeyDown={(e) => {
        if (e.key === "Escape") onClose();
      }}
    >
      <div className="modal" role="dialog" aria-modal="true" aria-label="Add to Priority Tracker"
        onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>Add to Priority Tracker</h3>
        </div>
        {error && <div className="error">{error}</div>}

        {created ? (
          <div>
            <p className="hint">
              Added as <strong>{created.itemId}</strong> — "{created.title}".
            </p>
            <div className="modal-actions">
              <button className="primary" type="button" onClick={onClose}>
                Done
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={submit}>
            <div className="form-row">
              <label htmlFor="atg-group">Group</label>
              <select id="atg-group" autoFocus value={groupId} onChange={(e) => setGroupId(e.target.value)} required>
                <option value="" disabled>
                  Select a group…
                </option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
              </select>
            </div>

            {checking && <p className="muted">Checking for an existing link…</p>}

            {existingItem ? (
              <div className="hint">
                This order is already linked to <strong>{existingItem.itemId}</strong> — "{existingItem.title}" in{" "}
                {group?.name}. There's no automatic replace — open Priority Backlog Tracker, switch to {group?.name},
                and edit that item directly instead.
                <div className="modal-actions">
                  <Link className="primary" to="/backlog/items" onClick={onClose}>
                    Go to Priority Backlog Tracker
                  </Link>
                  <button type="button" className="ghost" onClick={onClose}>
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              groupId &&
              !error && (
                <>
                  <div className="form-row">
                    <label htmlFor="atg-title">Title</label>
                    <input id="atg-title" value={title} onChange={(e) => setTitle(e.target.value)} required />
                  </div>
                  <div className="form-grid">
                    <div className="form-row">
                      <label htmlFor="atg-category">Category</label>
                      <select id="atg-category" value={category} onChange={(e) => setCategory(e.target.value)} required>
                        <option value="" disabled>
                          Select…
                        </option>
                        {categories.map((c) => (
                          <option key={c} value={c}>
                            {c}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="form-row">
                      <label htmlFor="atg-priority">Priority</label>
                      <select id="atg-priority" value={priority} onChange={(e) => setPriority(e.target.value)} required>
                        <option value="" disabled>
                          Select…
                        </option>
                        {priorities.map((p) => (
                          <option key={p} value={p}>
                            {p}
                          </option>
                        ))}
                      </select>
                    </div>
                  </div>
                  <div className="form-row">
                    <label htmlFor="atg-effort-value">Effort</label>
                    <div className="effort-input">
                      <input
                        id="atg-effort-value"
                        type="number"
                        min={1}
                        value={value}
                        onChange={(e) => setValue(e.target.value)}
                        aria-describedby="atg-effort-hint"
                      />
                      <label htmlFor="atg-effort-unit" className="sr-only">
                        Effort unit
                      </label>
                      <select id="atg-effort-unit" value={unit} onChange={(e) => setUnit(e.target.value as Unit)}>
                        <option value="MINUTES">min</option>
                        <option value="HOURS">hr</option>
                        <option value="DAYS">days</option>
                      </select>
                    </div>
                    <div id="atg-effort-hint" className={`hint${effortInvalid ? " bad" : ""}`}>
                      {UNIT_RULES[unit].hint}
                    </div>
                  </div>
                  <div className="form-grid">
                    <div className="form-row">
                      <label htmlFor="atg-due-date">Due date</label>
                      <input id="atg-due-date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
                    </div>
                    <div className="form-row">
                      <label htmlFor="atg-assignee">Assignee</label>
                      <select id="atg-assignee" value={ownerId} onChange={(e) => setOwnerId(e.target.value)}>
                        <option value="">Unassigned</option>
                        {group?.members.map((m) => (
                          <option key={m.id} value={m.id}>
                            {m.name}
                          </option>
                        ))}
                      </select>
                    </div>
                  </div>
                  <div className="form-row">
                    <label htmlFor="atg-notes">Notes</label>
                    <textarea id="atg-notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
                  </div>
                  <div className="modal-actions">
                    <button type="button" className="ghost" onClick={onClose} disabled={busy}>
                      Cancel
                    </button>
                    <button type="submit" className="primary" disabled={busy || effortInvalid}>
                      {busy ? "Adding…" : "Add to group"}
                    </button>
                  </div>
                </>
              )
            )}
          </form>
        )}
      </div>
    </div>
  );
}
