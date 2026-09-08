import { useEffect, useState } from "react";
import { api } from "../api/client";
import { PriorityBadge, TerminalBadge } from "../components/Badge";
import { formatDateTime, formatEffort } from "../lib/format";
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
                <th>ID</th>
                <th>Title</th>
                <th>Category</th>
                <th>Priority</th>
                <th>Effort</th>
                <th>Outcome</th>
                <th>Completed</th>
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
              {!loading && rows.length === 0 && (
                <tr>
                  <td colSpan={7} className="empty">
                    Nothing completed yet.
                  </td>
                </tr>
              )}
              {rows.map((a) => (
                <tr key={a.id}>
                  <td className="mono">{a.itemId}</td>
                  <td>{a.title}</td>
                  <td>{a.category}</td>
                  <td>
                    <PriorityBadge priority={a.priority} />
                  </td>
                  <td>{formatEffort(a.effort)}</td>
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
