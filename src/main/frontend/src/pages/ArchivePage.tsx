import { useCallback, useEffect, useState } from "react";
import { api } from "../api/client";
import { TerminalBadge } from "../components/Badge";
import { PriorityMark } from "../components/PriorityMark";
import { EffortIcon } from "../components/EffortIcon";
import { formatDateTime, effortLabel } from "../lib/format";
import { useItemsChanged } from "../lib/events";
import type { ArchivedItem, Page } from "../types";

const SIZE = 25;

export default function ArchivePage() {
  const [rows, setRows] = useState<ArchivedItem[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    const p = new URLSearchParams({ page: String(page), size: String(SIZE) });
    api
      .get<Page<ArchivedItem>>(`/archived?${p}`)
      .then((res) => {
        setRows(res.content);
        setTotal(res.total);
        setTotalPages(res.totalPages);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [page]);

  useEffect(() => {
    load();
  }, [load]);
  useItemsChanged(load);

  return (
    <div>
      <h1 className="page-title">Completed Items</h1>
      <p className="page-sub">
        Resolved, rejected, and archived items — {total} total. Read-only (§24).
      </p>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <div className="table-wrap">
          <table>
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
                  <td colSpan={6} className="empty">
                    Nothing completed yet.
                  </td>
                </tr>
              )}
              {rows.map((a) => (
                <tr key={a.id}>
                  <td>
                    <PriorityMark priority={a.priority} />
                  </td>
                  <td>{a.title}</td>
                  <td>{a.category}</td>
                  <td className="muted" style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <EffortIcon effort={a.effort} size={16} />
                    {effortLabel(a.effort)}
                  </td>
                  <td>
                    <TerminalBadge status={a.terminalStatus} />
                  </td>
                  <td>{formatDateTime(a.completionDate)}</td>
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
