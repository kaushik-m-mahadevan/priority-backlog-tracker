import { ReactNode } from "react";
import { useDock, DockSection } from "../dock/DockContext";
import { QuickGlyph, AttentionGlyph, TeamGlyph, ChevronIcon } from "./icons";
import { QuickWinsBody, AttentionBody, TeamBody } from "./rail/panels";
import type { Item, NeedsAttention, RankedItem, WorkloadOverview } from "../types";

interface Props {
  maxHeight?: number;
  quick: RankedItem[] | null;
  attention: NeedsAttention | null;
  team: WorkloadOverview | null;
  onOpen?: (item: Item) => void;
}

const META: Record<DockSection, { label: string; icon: ReactNode }> = {
  quick: { label: "Quick wins", icon: <QuickGlyph size={19} /> },
  attention: { label: "Needs attention", icon: <AttentionGlyph size={19} /> },
  team: { label: "Team workload", icon: <TeamGlyph size={19} /> },
};

export default function LeftDock({ maxHeight, quick, attention, team, onOpen }: Props) {
  const { shown, setShown, active, setActive } = useDock();
  if (!shown) return null;

  const counts: Record<DockSection, number | undefined> = {
    quick: quick?.length,
    attention:
      (attention?.staleAndOverdue.length ?? 0) + (attention?.buriedLowPriority.length ?? 0),
    team: team?.owners.length,
  };

  function body(s: DockSection) {
    if (s === "quick") return <QuickWinsBody rows={quick} onOpen={onOpen} />;
    if (s === "attention") return <AttentionBody data={attention} onOpen={onOpen} />;
    return <TeamBody data={team} />;
  }

  return (
    <div className="dock">
      <div className="dock-strip">
        <button
          className="iconbtn"
          title="Hide panels"
          aria-label="Hide panels"
          onClick={() => setShown(false)}
        >
          <ChevronIcon open={false} size={17} />
        </button>
        {(Object.keys(META) as DockSection[]).map((s) => (
          <button
            key={s}
            className={`dock-tab${active === s ? " on" : ""}`}
            title={META[s].label}
            aria-label={META[s].label}
            onClick={() => setActive(active === s ? null : s)}
          >
            {META[s].icon}
            {counts[s] ? <span className="tab-count">{counts[s]}</span> : null}
          </button>
        ))}
      </div>

      {active && (
        <section className="dock-panel" style={maxHeight ? { maxHeight } : undefined}>
          <header>
            <span className="rp-title">{META[active].label}</span>
            <button className="iconbtn" aria-label="Collapse" onClick={() => setActive(null)}>
              <span style={{ transform: "rotate(180deg)", display: "inline-flex" }}>
                <ChevronIcon size={15} />
              </span>
            </button>
          </header>
          <div className="dock-body">{body(active)}</div>
        </section>
      )}
    </div>
  );
}
