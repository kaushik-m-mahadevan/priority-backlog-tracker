import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { PriorityBadge } from "../components/Badge";
import { dueLabel, formatEffort, score } from "../lib/format";
import type { NeedsAttention, RankedItem, WorkloadOverview } from "../types";

export default function DashboardPage() {
  const [top, setTop] = useState<RankedItem[] | null>(null);
  const [quick, setQuick] = useState<RankedItem[] | null>(null);
  const [attention, setAttention] = useState<NeedsAttention | null>(null);
  const [workload, setWorkload] = useState<WorkloadOverview | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      api.get<RankedItem[]>("/items/top?limit=10"),
      api.get<RankedItem[]>("/items/quick-wins?limit=10"),
      api.get<NeedsAttention>("/insights/needs-attention"),
      api.get<WorkloadOverview>("/insights/workload"),
    ])
      .then(([t, q, a, w]) => {
        setTop(t);
        setQuick(q);
        setAttention(a);
        setWorkload(w);
      })
      .catch((e) => setError(e.message));
  }, []);

  return (
    <div>
      <h1 className="page-title">Dashboard</h1>
      <p className="page-sub">What to work on next, and what's slipping.</p>
      {error && <div className="error">{error}</div>}

      <div className="grid cols-2">
        <RankedCard title="Top 10" subtitle="by priority score" rows={top} showScore />
        <RankedCard title="Quick Wins" subtitle="fastest to finish" rows={quick} />
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>
          Needs Attention{" "}
          <span className="count">
            {attention
              ? attention.staleAndOverdue.length + attention.buriedLowPriority.length
              : ""}
          </span>
        </h2>
        <div className="grid cols-2">
          <div>
            <h3 style={{ fontSize: 13 }} className="muted">
              Stale &amp; Overdue
            </h3>
            {attention && attention.staleAndOverdue.length === 0 && (
              <p className="empty">Nothing overdue past the threshold.</p>
            )}
            <ul style={{ paddingLeft: 18, margin: 0 }}>
              {attention?.staleAndOverdue.map((f) => (
                <li key={f.item.id}>
                  <Link to="/items">{f.item.title}</Link>{" "}
                  <span className="overdue">· {f.days}d overdue</span>
                </li>
              ))}
            </ul>
          </div>
          <div>
            <h3 style={{ fontSize: 13 }} className="muted">
              Buried Low-Priority
            </h3>
            {attention && attention.buriedLowPriority.length === 0 && (
              <p className="empty">Nothing buried.</p>
            )}
            <ul style={{ paddingLeft: 18, margin: 0 }}>
              {attention?.buriedLowPriority.map((f) => (
                <li key={f.item.id}>
                  <Link to="/items">{f.item.title}</Link>{" "}
                  <span className="muted">· {f.days}d old</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>Owner Workload</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Owner</th>
                <th>Open</th>
                <th>Critical / High</th>
                <th>By category</th>
              </tr>
            </thead>
            <tbody>
              {workload?.owners.map((o) => (
                <tr key={o.ownerId ?? "unassigned"}>
                  <td>{o.ownerName}</td>
                  <td>
                    <span className="stat">
                      <span className="n">{o.openCount}</span>
                    </span>
                  </td>
                  <td>{o.criticalHighCount}</td>
                  <td>
                    <div className="kv">
                      {Object.entries(o.byCategory).map(([c, n]) => (
                        <span className="chip" key={c}>
                          {c} {n}
                        </span>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
              {workload && workload.owners.length === 0 && (
                <tr>
                  <td colSpan={4} className="empty">
                    No open items.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function RankedCard({
  title,
  subtitle,
  rows,
  showScore,
}: {
  title: string;
  subtitle: string;
  rows: RankedItem[] | null;
  showScore?: boolean;
}) {
  return (
    <div className="card">
      <h2>
        {title} <span className="count">· {subtitle}</span>
      </h2>
      {rows && rows.length === 0 && <p className="empty">No items yet.</p>}
      <div className="table-wrap">
        <table>
          <tbody>
            {rows?.map((r, i) => (
              <tr key={r.item.id}>
                <td className="mono">{i + 1}</td>
                <td>
                  <div>{r.item.title}</div>
                  <div className="muted" style={{ fontSize: 12 }}>
                    {r.item.itemId} · {formatEffort(r.item.effort)} · {dueLabel(r.item.dueDate)}
                  </div>
                </td>
                <td>
                  <PriorityBadge priority={r.item.priority} />
                </td>
                {showScore && (
                  <td style={{ minWidth: 90 }}>
                    <div className="score-bar">
                      <span style={{ width: `${Math.min(100, r.sortScore * 100)}%` }} />
                    </div>
                    <span className="mono">{score(r.sortScore)}</span>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
