import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { useItemsChanged } from "../lib/events";
import { useGroups } from "../groups/GroupContext";
import NoGroupNotice from "../components/NoGroupNotice";
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
  const { currentGroupId } = useGroups();
  const [rows, setRows] = useState<RankedItem[] | null>(null);
  const [editing, setEditing] = useState<Item | null>(null);
  function load() {
    if (!currentGroupId) return;
    api.get<RankedItem[]>(`/items/quick-wins?limit=12&groupId=${currentGroupId}`).then(setRows).catch(() => setRows([]));
  }
  useEffect(load, [currentGroupId]);
  useItemsChanged(load);
  if (!currentGroupId) return <NoGroupNotice />;
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
  const { currentGroupId } = useGroups();
  const [data, setData] = useState<NeedsAttention | null>(null);
  const [editing, setEditing] = useState<Item | null>(null);
  const [params, setParams] = useSearchParams();
  function load() {
    if (!currentGroupId) return;
    api.get<NeedsAttention>(`/insights/needs-attention?groupId=${currentGroupId}`).then(setData).catch(() => setData(null));
  }
  useEffect(load, [currentGroupId]);
  useItemsChanged(load);

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
  const { currentGroupId } = useGroups();
  const [data, setData] = useState<WorkloadOverview | null>(null);
  useEffect(() => {
    if (!currentGroupId) return;
    api
      .get<WorkloadOverview>(`/insights/workload?groupId=${currentGroupId}`)
      .then(setData)
      .catch(() => setData(null));
  }, [currentGroupId]);
  if (!currentGroupId) return <NoGroupNotice />;
  return (
    <Shell title="Workload" sub="Who's holding what in this group.">
      <TeamBody data={data} />
    </Shell>
  );
}
