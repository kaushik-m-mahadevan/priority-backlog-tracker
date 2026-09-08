import { useEffect, useMemo, useState } from "react";
import { api } from "../api/client";
import { StatusBadge } from "../components/Badge";
import { PriorityMark } from "../components/PriorityMark";
import ItemFormModal from "../components/ItemFormModal";
import { useConfig } from "../config/ConfigContext";
import { dueChip, effortSpan, formatDate } from "../lib/format";
import type { Item } from "../types";

export default function ItemsPage() {
  const config = useConfig();
  const [items, setItems] = useState<Item[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Item | null>(null);
  const [menuFor, setMenuFor] = useState<string | null>(null);

  const [q, setQ] = useState("");
  const [fCat, setFCat] = useState("");
  const [fPrio, setFPrio] = useState("");

  function load() {
    setLoading(true);
    api
      .get<Item[]>("/items")
      .then(setItems)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }
  useEffect(load, []);

  const filtered = useMemo(
    () =>
      items.filter(
        (i) =>
          (!q || i.title.toLowerCase().includes(q.toLowerCase())) &&
          (!fCat || i.category === fCat) &&
          (!fPrio || i.priority === fPrio),
      ),
    [items, q, fCat, fPrio],
  );

  async function setStatus(i: Item, status: "BACKLOG" | "IN_PROGRESS") {
    await api.patch(`/items/${i.id}/status`, { status });
    load();
  }
  async function complete(i: Item, terminalStatus: string) {
    await api.post(`/items/${i.id}/complete`, { terminalStatus });
    setMenuFor(null);
    load();
  }

  return (
    <div>
      <h1 className="page-title">Items</h1>
      <p className="page-sub">Live backlog — {items.length} open.</p>
      {error && <div className="error">{error}</div>}

      <div className="toolbar">
        <input placeholder="Search title…" value={q} onChange={(e) => setQ(e.target.value)} />
        <select value={fCat} onChange={(e) => setFCat(e.target.value)}>
          <option value="">All categories</option>
          {config?.categories.map((c) => (
            <option key={c}>{c}</option>
          ))}
        </select>
        <select value={fPrio} onChange={(e) => setFPrio(e.target.value)}>
          <option value="">All priorities</option>
          {config?.priorities.map((p) => (
            <option key={p}>{p}</option>
          ))}
        </select>
        <span className="spacer" />
        <button
          className="primary"
          onClick={() => {
            setEditing(null);
            setShowForm(true);
          }}
        >
          + New item
        </button>
      </div>

      <div className="card">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th style={{ width: 28 }}></th>
                <th>Title</th>
                <th>Category</th>
                <th>Effort</th>
                <th>Due</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={7} className="empty">
                    Loading…
                  </td>
                </tr>
              )}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td colSpan={7} className="empty">
                    No items match.
                  </td>
                </tr>
              )}
              {filtered.map((i) => {
                const due = dueChip(i.dueDate);
                return (
                <tr key={i.id}>
                  <td>
                    <PriorityMark priority={i.priority} />
                  </td>
                  <td>{i.title}</td>
                  <td>{i.category}</td>
                  <td className="muted">{effortSpan(i.effort)}</td>
                  <td>
                    <span
                      className={`chip ${due.tone === "late" ? "late" : due.tone === "soon" ? "soon" : ""}`}
                    >
                      {due.text}
                    </span>
                    <div className="muted" style={{ fontSize: 11, marginTop: 2 }}>
                      {formatDate(i.dueDate)}
                    </div>
                  </td>
                  <td>
                    <StatusBadge status={i.status} />
                  </td>
                  <td>
                    <div className="row-actions">
                      <button
                        onClick={() => {
                          setEditing(i);
                          setShowForm(true);
                        }}
                      >
                        Edit
                      </button>
                      {i.status === "BACKLOG" ? (
                        <button onClick={() => setStatus(i, "IN_PROGRESS")}>Start</button>
                      ) : (
                        <button onClick={() => setStatus(i, "BACKLOG")}>Back to backlog</button>
                      )}
                      <div style={{ position: "relative", display: "inline-block" }}>
                        <button onClick={() => setMenuFor(menuFor === i.id ? null : i.id)}>
                          Complete ▾
                        </button>
                        {menuFor === i.id && (
                          <div
                            className="card"
                            style={{
                              position: "absolute",
                              right: 0,
                              top: "110%",
                              padding: 6,
                              zIndex: 20,
                              minWidth: 140,
                            }}
                          >
                            {["RESOLVED", "REJECTED", "ARCHIVED"].map((t) => (
                              <button
                                key={t}
                                className="ghost"
                                style={{ display: "block", width: "100%", textAlign: "left" }}
                                onClick={() => complete(i, t)}
                              >
                                {t[0] + t.slice(1).toLowerCase()}
                              </button>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  </td>
                </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {showForm && (
        <ItemFormModal
          existing={editing}
          onClose={() => setShowForm(false)}
          onSaved={() => {
            setShowForm(false);
            load();
          }}
        />
      )}
    </div>
  );
}
