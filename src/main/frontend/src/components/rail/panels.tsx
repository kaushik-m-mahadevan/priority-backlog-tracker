import { AgeIcon, OverdueIcon } from "../icons";
import { EffortIcon } from "../EffortIcon";
import { Creature } from "../Creature";
import { ageShort, effortLabel } from "../../lib/format";
import type { Item, NeedsAttention, RankedItem, WorkloadOverview } from "../../types";

type OpenFn = ((item: Item) => void) | undefined;

function Row({ item, onOpen, children }: { item: Item; onOpen: OpenFn; children: React.ReactNode }) {
  if (!onOpen) return <div className="rp-item">{children}</div>;
  return (
    <div
      className="rp-item clickable"
      role="button"
      tabIndex={0}
      onClick={() => onOpen(item)}
      onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && onOpen(item)}
    >
      {children}
    </div>
  );
}

export function QuickWinsBody({ rows, onOpen }: { rows: RankedItem[] | null; onOpen?: OpenFn }) {
  if (!rows) return <p className="empty">Loading…</p>;
  if (rows.length === 0) return <p className="empty">Nothing quick right now — that's fine.</p>;
  const [pick, ...rest] = rows;
  return (
    <>
      <div
        className={`rp-pick${onOpen ? " clickable" : ""}`}
        role={onOpen ? "button" : undefined}
        tabIndex={onOpen ? 0 : undefined}
        onClick={onOpen ? () => onOpen(pick.item) : undefined}
        onKeyDown={
          onOpen
            ? (e) => (e.key === "Enter" || e.key === " ") && onOpen(pick.item)
            : undefined
        }
      >
        <div className="lead">got 15 minutes?</div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <EffortIcon effort={pick.item.effort} size={22} />
          {pick.item.title}
        </div>
      </div>
      {rest.map((r) => (
        <Row key={r.item.id} item={r.item} onOpen={onOpen}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <EffortIcon effort={r.item.effort} size={18} />
            {r.item.title}
          </div>
          <div className="sub">{effortLabel(r.item.effort)}</div>
        </Row>
      ))}
    </>
  );
}

export function AttentionBody({ data, onOpen }: { data: NeedsAttention | null; onOpen?: OpenFn }) {
  if (!data) return <p className="empty">Loading…</p>;
  const { staleAndOverdue: stale, buriedLowPriority: buried } = data;
  if (stale.length + buried.length === 0)
    return <p className="empty">Nothing slipping. Nice and calm.</p>;
  return (
    <>
      {stale.map((f) => (
        <Row key={f.item.id} item={f.item} onOpen={onOpen}>
          {f.item.title}
          <div
            className="sub hot"
            title={`Flagged because it is ${ageShort(f.days)} past its due date and still open.`}
          >
            <OverdueIcon /> {ageShort(f.days)} past due
          </div>
        </Row>
      ))}
      {buried.map((f) => (
        <Row key={f.item.id} item={f.item} onOpen={onOpen}>
          {f.item.title}
          <div
            className="sub"
            title={`Flagged because it is low-priority and has sat untouched for ${ageShort(
              f.days,
            )} — long enough that it's likely being forgotten.`}
          >
            <AgeIcon /> {ageShort(f.days)} untouched
          </div>
        </Row>
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
