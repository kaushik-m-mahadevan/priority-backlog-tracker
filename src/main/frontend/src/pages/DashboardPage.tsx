import { useEffect, useState } from "react";
import { api } from "../api/client";
import ItemFormModal from "../components/ItemFormModal";
import RailPanel from "../components/RailPanel";
import { PriorityMark } from "../components/PriorityMark";
import { dueChip, effortSpan, effortWeight } from "../lib/format";
import type { Item, NeedsAttention, RankedItem, WorkloadOverview } from "../types";

export default function DashboardPage() {
  const [top, setTop] = useState<RankedItem[] | null>(null);
  const [quick, setQuick] = useState<RankedItem[] | null>(null);
  const [attention, setAttention] = useState<NeedsAttention | null>(null);
  const [workload, setWorkload] = useState<WorkloadOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<Item | null>(null);

  function load() {
    Promise.all([
      api.get<RankedItem[]>("/items/top?limit=10"),
      api.get<RankedItem[]>("/items/quick-wins?limit=8"),
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
  }
  useEffect(load, []);

  const attnCount =
    (attention?.staleAndOverdue.length ?? 0) + (attention?.buriedLowPriority.length ?? 0);

  return (
    <div>
      <h1 className="page-title">Priority</h1>
      <p className="page-sub">The ten things that matter most right now.</p>
      {error && <div className="error">{error}</div>}

      <div className="dash">
        <div className="plist">
          {top && top.length === 0 && <div className="empty" style={{ padding: 16 }}>Nothing in the backlog yet.</div>}
          {top?.map((r) => {
            const due = dueChip(r.item.dueDate);
            return (
              <div
                key={r.item.id}
                className="prow"
                onClick={() => setEditing(r.item)}
                role="button"
              >
                <PriorityMark priority={r.item.priority} />
                <span className="title">{r.item.title}</span>
                {r.item.status === "IN_PROGRESS" && <span className="in-progress">in progress</span>}
                <span className="meta">
                  <span
                    className="wbar"
                    title={effortSpan(r.item.effort)}
                    aria-label={effortSpan(r.item.effort)}
                  >
                    <span style={{ width: `${Math.max(6, effortWeight(r.item.effort) * 100)}%` }} />
                  </span>
                  <span className={`chip ${due.tone === "late" ? "late" : due.tone === "soon" ? "soon" : ""}`}>
                    {due.text}
                  </span>
                </span>
              </div>
            );
          })}
        </div>

        <aside className="rail">
          <RailPanel id="quickwins" title="Quick wins" count={quick?.length ?? 0}>
            {quick && quick.length === 0 && <p className="empty">Nothing quick right now.</p>}
            {quick?.map((r) => (
              <div className="rp-item" key={r.item.id}>
                {r.item.title}
                <div className="sub">{effortSpan(r.item.effort)}</div>
              </div>
            ))}
          </RailPanel>

          <RailPanel id="attention" title="Needs attention" count={attnCount}>
            {attnCount === 0 && <p className="empty">Nothing slipping.</p>}
            {attention?.staleAndOverdue.map((f) => (
              <div className="rp-item" key={f.item.id}>
                {f.item.title}
                <div className="sub overdue">{f.days}d overdue</div>
              </div>
            ))}
            {attention?.buriedLowPriority.map((f) => (
              <div className="rp-item" key={f.item.id}>
                {f.item.title}
                <div className="sub">buried · {f.days}d old</div>
              </div>
            ))}
          </RailPanel>

          <RailPanel id="workload" title="Team workload" count={workload?.owners.length ?? 0}>
            {workload?.owners.map((o) => (
              <div className="rp-item" key={o.ownerId ?? "unassigned"}>
                {o.ownerName}
                <div className="sub">
                  {o.openCount} open
                  {o.criticalHighCount > 0 && ` · ${o.criticalHighCount} hot`}
                </div>
              </div>
            ))}
          </RailPanel>
        </aside>
      </div>

      {editing && (
        <ItemFormModal
          existing={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            load();
          }}
        />
      )}
    </div>
  );
}
