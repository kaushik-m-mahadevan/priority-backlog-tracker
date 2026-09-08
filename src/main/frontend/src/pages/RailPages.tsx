import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { QuickWinsBody, AttentionBody, TeamBody } from "../components/rail/panels";
import ItemFormModal from "../components/ItemFormModal";
import type { Item, NeedsAttention, RankedItem, WorkloadOverview } from "../types";

function Shell({ title, sub, children }: { title: string; sub: string; children: React.ReactNode }) {
  return (
    <div>
      <h1 className="page-title">{title}</h1>
      <p className="page-sub">{sub}</p>
      <div className="card">{children}</div>
    </div>
  );
}

export function QuickWinsPage() {
  const [rows, setRows] = useState<RankedItem[] | null>(null);
  const [editing, setEditing] = useState<Item | null>(null);
  function load() {
    api.get<RankedItem[]>("/items/quick-wins?limit=12").then(setRows).catch(() => setRows([]));
  }
  useEffect(load, []);
  return (
    <Shell title="Quick wins" sub="Small things you can close out now.">
      <QuickWinsBody rows={rows} onOpen={setEditing} />
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
    </Shell>
  );
}

export function AttentionPage() {
  const [data, setData] = useState<NeedsAttention | null>(null);
  const [editing, setEditing] = useState<Item | null>(null);
  const [params, setParams] = useSearchParams();
  function load() {
    api.get<NeedsAttention>("/insights/needs-attention").then(setData).catch(() => setData(null));
  }
  useEffect(load, []);

  // deep link from the bell: /attention?open=<id>
  useEffect(() => {
    const id = params.get("open");
    if (!id || !data) return;
    const hit = [...data.staleAndOverdue, ...data.buriedLowPriority].find((f) => f.item.id === id);
    if (hit) setEditing(hit.item);
    setParams({}, { replace: true });
  }, [data, params, setParams]);
  return (
    <Shell title="Needs attention" sub="Quietly slipping — a gentle nudge, no rush.">
      <AttentionBody data={data} onOpen={setEditing} />
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
    </Shell>
  );
}

export function TeamPage() {
  const [data, setData] = useState<WorkloadOverview | null>(null);
  useEffect(() => {
    api.get<WorkloadOverview>("/insights/workload").then(setData).catch(() => setData(null));
  }, []);
  return (
    <Shell title="Team workload" sub="Who's holding what.">
      <TeamBody data={data} />
    </Shell>
  );
}
