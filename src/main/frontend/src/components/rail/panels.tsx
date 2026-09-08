import { AgeIcon, OverdueIcon } from "../icons";
import { EffortIcon } from "../EffortIcon";
import { Creature } from "../Creature";
import { ageShort, effortLabel } from "../../lib/format";
import type { NeedsAttention, RankedItem, WorkloadOverview } from "../../types";

export function QuickWinsBody({ rows }: { rows: RankedItem[] | null }) {
  if (!rows) return <p className="empty">Loading…</p>;
  if (rows.length === 0) return <p className="empty">Nothing quick right now — that's fine.</p>;
  const [pick, ...rest] = rows;
  return (
    <>
      <div className="rp-pick">
        <div className="lead">got 15 minutes?</div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <EffortIcon effort={pick.item.effort} size={22} />
          {pick.item.title}
        </div>
      </div>
      {rest.map((r) => (
        <div className="rp-item" key={r.item.id}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <EffortIcon effort={r.item.effort} size={18} />
            {r.item.title}
          </div>
          <div className="sub">{effortLabel(r.item.effort)}</div>
        </div>
      ))}
    </>
  );
}

export function AttentionBody({ data }: { data: NeedsAttention | null }) {
  if (!data) return <p className="empty">Loading…</p>;
  const { staleAndOverdue: stale, buriedLowPriority: buried } = data;
  if (stale.length + buried.length === 0)
    return <p className="empty">Nothing slipping. Nice and calm.</p>;
  return (
    <>
      {stale.map((f) => (
        <div className="rp-item" key={f.item.id}>
          {f.item.title}
          <div className="sub hot">
            <OverdueIcon /> {ageShort(f.days)} past due
          </div>
        </div>
      ))}
      {buried.map((f) => (
        <div className="rp-item" key={f.item.id}>
          {f.item.title}
          <div className="sub">
            <AgeIcon /> {ageShort(f.days)} untouched
          </div>
        </div>
      ))}
    </>
  );
}

export function TeamBody({ data }: { data: WorkloadOverview | null }) {
  if (!data) return <p className="empty">Loading…</p>;
  if (data.owners.length === 0) return <p className="empty">No open work.</p>;
  return (
    <>
      {data.owners.map((o) => (
        <div className="rp-item" key={o.ownerId ?? "unassigned"}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <Creature seed={o.ownerId} label={o.ownerName} size={22} />
            {o.ownerName}
          </div>
          <div className="sub">
            {o.openCount} open{o.criticalHighCount > 0 && ` · ${o.criticalHighCount} pressing`}
          </div>
        </div>
      ))}
    </>
  );
}
