import { useCallback, useEffect, useState } from "react";
import { api } from "../api/client";
import { TerminalBadge } from "../components/Badge";
import { PriorityMark } from "../components/PriorityMark";
import { EffortIcon } from "../components/EffortIcon";
import { formatDateTime, effortLabel } from "../lib/format";
import { useItemsChanged } from "../lib/events";
import EmptyLeaf from "../components/EmptyLeaf";
import NoGroupNotice from "../components/NoGroupNotice";
import { useGroups } from "../groups/GroupContext";
import type { ArchivedItem, Page } from "../types";

const SIZE = 25;

export default function ArchivePage() {
  const { currentGroupId } = useGroups();
  const [rows, setRows] = useState<ArchivedItem[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!currentGroupId) return;
    setLoading(true);
    const p = new URLSearchParams({ page: String(page), size: String(SIZE), groupId: currentGroupId });
    api
      .get<Page<ArchivedItem>>(`/archived?${p}`)
      .then((res) => {
        setRows(res.content);
        setTotal(res.total);
        setTotalPages(res.totalPages);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [page, currentGroupId]);

  useEffect(() => {
    load();
  }, [load]);
  useItemsChanged(load);

  if (!currentGroupId) return <NoGroupNotice />;

  return (
    <div>
      <h1 className="page-title">Completed Items</h1>
      <p className="page-sub">
        Resolved, rejected, and archived items — {total} total. Read-only — once an item lands
        here it can't be edited or brought back to the active list.
      </p>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: 28 }}></th>
                <th>Title</th>
                <th>Category</th>
                <th>Effort</th>
                <th>Outcome</th>
                <th>Completed</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={6} className="empty">
                    Loading…
                  </td>
                </tr>
              )}
              {!loading && rows.length === 0 && (
                <tr>
                  <td colSpan={6}>
                    <EmptyLeaf message="Nothing completed yet — the tree is still a seed." />
                  </td>
                </tr>
              )}
              {rows.map((a) => (
                <tr key={a.id}>
                  <td className="cell-prio">
                    <PriorityMark priority={a.priority} />
                  </td>
                  <td className="cell-title">{a.title}</td>
                  <td className="cell-cat">{a.category}</td>
                  <td className="cell-effort muted" style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <EffortIcon effort={a.effort} size={16} />
                    <span className="cell-lbl">{effortLabel(a.effort)}</span>
                  </td>
                  <td className="cell-status">
                    <TerminalBadge status={a.terminalStatus} />
                  </td>
                  <td className="cell-due">{formatDateTime(a.completionDate)}</td>
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
    </div>
  );
}
