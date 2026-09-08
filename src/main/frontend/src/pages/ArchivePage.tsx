import { useEffect, useState } from "react";
import { api } from "../api/client";
import { TerminalBadge } from "../components/Badge";
import { PriorityMark } from "../components/PriorityMark";
import { formatDateTime, effortSpan } from "../lib/format";
import type { ArchivedItem } from "../types";

export default function ArchivePage() {
  const [rows, setRows] = useState<ArchivedItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<ArchivedItem[]>("/archived")
      .then(setRows)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <h1 className="page-title">Completed Items</h1>
      <p className="page-sub">
        Resolved, rejected, and archived items — {rows.length} total. Read-only (§24).
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
                  <td className="muted">{effortSpan(a.effort)}</td>
                  <td>
                    <TerminalBadge status={a.terminalStatus} />
                  </td>
                  <td>{formatDateTime(a.completionDate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
