import { ReactNode } from "react";
import { useDock, DockSection } from "../dock/DockContext";
import { QuickGlyph, AttentionGlyph, TeamGlyph, ChevronIcon } from "./icons";

import { QuickWinsBody, AttentionBody, TeamBody } from "./rail/panels";
import type { Item, NeedsAttention, RankedItem, WorkloadOverview } from "../types";

interface Props {
  quick: RankedItem[] | null;
  attention: NeedsAttention | null;
  team: WorkloadOverview | null;
  onOpen?: (item: Item) => void;
}

const ORDER: DockSection[] = ["quick", "attention", "team"];
const META: Record<DockSection, { label: string; icon: ReactNode }> = {
  quick: { label: "Quick wins", icon: <QuickGlyph size={17} /> },
  attention: { label: "Needs attention", icon: <AttentionGlyph size={17} /> },
  team: { label: "Team workload", icon: <TeamGlyph size={17} /> },
};

/**
 * The dashboard side panels. Hidden until "Show panels"; then a full-height
 * accordion — exactly one section open at a time, Quick wins first by default.
 */
export default function LeftDock({ quick, attention, team, onOpen }: Props) {
  const { shown, setShown, active, setActive } = useDock();
  if (!shown) return null;

  const open: DockSection = active ?? "quick";

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
    <aside className="dock-acc">
      <div className="dock-acc-head">
        <span className="rp-title">Panels</span>
        <button className="dock-collapse-btn" aria-label="Hide panels" title="Hide panels" onClick={() => setShown(false)}>
          ‹
        </button>
      </div>

      {ORDER.map((s) => {
        const isOpen = s === open;
        return (
          <section key={s} className={`acc-sec${isOpen ? " open" : ""}`}>
            <button
              className="acc-hdr"
              aria-expanded={isOpen}
              onClick={() => setActive(s)}
            >
              <span className="acc-ico">{META[s].icon}</span>
              <span className="acc-lbl">{META[s].label}</span>
              {counts[s] ? <span className="acc-cnt">{counts[s]}</span> : null}
              <span className="acc-chev">
                <ChevronIcon open={isOpen} size={13} />
              </span>
            </button>
            {isOpen && <div className="acc-body">{body(s)}</div>}
          </section>
        );
      })}
    </aside>
  );
}
