import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import { StatusBadge } from "../components/Badge";
import { PriorityMark } from "../components/PriorityMark";
import { Creature } from "../components/Creature";
import { useUsers } from "../users/UsersContext";
import { EffortIcon } from "../components/EffortIcon";
import { DueMark } from "../components/DueMark";
import ItemFormModal from "../components/ItemFormModal";
import EmptyLeaf from "../components/EmptyLeaf";
import NoGroupNotice from "../components/NoGroupNotice";
import { useGroups } from "../groups/GroupContext";
import { useConfig } from "../config/ConfigContext";
import { useItemsChanged, notifyItemsChanged } from "../lib/events";
import { effortLabel, formatDate } from "../lib/format";
import type { Item, Page } from "../types";

const SIZE = 25;

export default function ItemsPage() {
  const config = useConfig();
  const { nameOf } = useUsers();
  const { currentGroupId } = useGroups();
  const [items, setItems] = useState<Item[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Item | null>(null);
  const [menuFor, setMenuFor] = useState<string | null>(null);

  const [q, setQ] = useState("");
  const [fCat, setFCat] = useState("");
  const [fPrio, setFPrio] = useState("");

  const load = useCallback(() => {
    if (!currentGroupId) return;
    setLoading(true);
    const p = new URLSearchParams({ page: String(page), size: String(SIZE), groupId: currentGroupId });
    if (q.trim()) p.set("q", q.trim());
    if (fCat) p.set("category", fCat);
    if (fPrio) p.set("priority", fPrio);
    api
      .get<Page<Item>>(`/items?${p}`)
      .then((res) => {
        setItems(res.content);
        setTotal(res.total);
        setTotalPages(res.totalPages);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [q, fCat, fPrio, page, currentGroupId]);

  // reset to the first page whenever a filter changes
  const filterKey = `${q}|${fCat}|${fPrio}`;
  const prevKey = useRef(filterKey);
  useEffect(() => {
    if (prevKey.current !== filterKey) {
      prevKey.current = filterKey;
      if (page !== 0) setPage(0);
    }
  }, [filterKey, page]);

  // debounce the fetch
  useEffect(() => {
    const t = setTimeout(load, 250);
    return () => clearTimeout(t);
  }, [load]);
  useItemsChanged(load);

  async function setStatus(i: Item, status: "BACKLOG" | "IN_PROGRESS") {
    await api.patch(`/items/${i.id}/status`, { status });
    notifyItemsChanged();
  }
  async function complete(i: Item, terminalStatus: string) {
    await api.post(`/items/${i.id}/complete`, { terminalStatus });
    setMenuFor(null);
    notifyItemsChanged();
  }

  if (!currentGroupId) return <NoGroupNotice />;

  return (
    <div>
      <h1 className="page-title">Items</h1>
      <p className="page-sub">Live backlog — {total} open.</p>
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
                <th style={{ width: 30 }}></th>
                <th style={{ width: 18 }}></th>
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
                  <td colSpan={8} className="empty">
                    Loading…
                  </td>
                </tr>
              )}
              {!loading && items.length === 0 && (
                <tr>
                  <td colSpan={8}>
                    <EmptyLeaf
                      message={
                        q || fCat || fPrio
                          ? "No items match those filters."
                          : "No open items yet."
                      }
                    />
                  </td>
                </tr>
              )}
              {items.map((i) => (
                <tr key={i.id}>
                  <td>
                    <Creature
                      seed={i.ownerId}
                      label={nameOf(i.ownerId)}
                      inProgress={i.status === "IN_PROGRESS"}
                      size={24}
                    />
                  </td>
                  <td>
                    <PriorityMark priority={i.priority} />
                  </td>
                  <td>{i.title}</td>
                  <td>{i.category}</td>
                  <td className="muted" style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <EffortIcon effort={i.effort} size={16} />
                    {effortLabel(i.effort)}
                  </td>
                  <td>
                    <DueMark iso={i.dueDate} />
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
              ))}
            </tbody>
          </table>
        </div>
        {totalPages > 1 && (
          <div className="pager">
            <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              ‹ Prev
            </button>
            <span className="muted">
              Page {page + 1} of {totalPages}
            </span>
            <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
              Next ›
            </button>
          </div>
        )}
      </div>

      {showForm && (
        <ItemFormModal
          existing={editing}
          onClose={() => setShowForm(false)}
          onSaved={() => {
            setShowForm(false);
            load();
          }}
          onComplete={(t) => {
            if (editing) complete(editing, t);
            setShowForm(false);
          }}
        />
      )}
    </div>
  );
}
