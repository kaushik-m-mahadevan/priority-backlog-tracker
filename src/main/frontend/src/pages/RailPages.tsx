import { useEffect, useState } from "react";
import { api } from "../api/client";
import { QuickWinsBody, AttentionBody, TeamBody } from "../components/rail/panels";
import type { NeedsAttention, RankedItem, WorkloadOverview } from "../types";

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
  useEffect(() => {
    api.get<RankedItem[]>("/items/quick-wins?limit=12").then(setRows).catch(() => setRows([]));
  }, []);
  return (
    <Shell title="Quick wins" sub="Small things you can close out now.">
      <QuickWinsBody rows={rows} />
    </Shell>
  );
}

export function AttentionPage() {
  const [data, setData] = useState<NeedsAttention | null>(null);
  useEffect(() => {
    api.get<NeedsAttention>("/insights/needs-attention").then(setData).catch(() => setData(null));
  }, []);
  return (
    <Shell title="Needs attention" sub="Quietly slipping — a gentle nudge, no rush.">
      <AttentionBody data={data} />
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
