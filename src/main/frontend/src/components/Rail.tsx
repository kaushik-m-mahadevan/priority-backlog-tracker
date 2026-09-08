import { ReactNode, useState } from "react";
import { ChevronIcon, QuickGlyph, AttentionGlyph, TeamGlyph } from "./icons";
import { QuickWinsBody, AttentionBody, TeamBody } from "./rail/panels";
import type { NeedsAttention, RankedItem, WorkloadOverview } from "../types";

type Key = "quick" | "attention" | "team";
const STORE = "pbt.rail.open";

function readOpen(): Key {
  try {
    const v = sessionStorage.getItem(STORE);
    return v === "attention" || v === "team" ? v : "quick";
  } catch {
    return "quick";
  }
}

interface Props {
  maxHeight?: number;
  quick: RankedItem[] | null;
  attention: NeedsAttention | null;
  team: WorkloadOverview | null;
}

export default function Rail({ maxHeight, quick, attention, team }: Props) {
  const [open, setOpen] = useState<Key>(readOpen);

  function toggle(k: Key) {
    setOpen(k);
    try {
      sessionStorage.setItem(STORE, k);
    } catch {
      /* ignore */
    }
  }

  const attnCount =
    (attention?.staleAndOverdue.length ?? 0) + (attention?.buriedLowPriority.length ?? 0);

  return (
    <div className="rail" style={maxHeight ? { maxHeight } : undefined}>
      <Section
        icon={<QuickGlyph />}
        title="Quick wins"
        count={quick?.length}
        open={open === "quick"}
        onToggle={() => toggle("quick")}
      >
        <QuickWinsBody rows={quick} />
      </Section>
      <Section
        icon={<AttentionGlyph />}
        title="Needs attention"
        count={attnCount}
        open={open === "attention"}
        onToggle={() => toggle("attention")}
      >
        <AttentionBody data={attention} />
      </Section>
      <Section
        icon={<TeamGlyph />}
        title="Team workload"
        count={team?.owners.length}
        open={open === "team"}
        onToggle={() => toggle("team")}
      >
        <TeamBody data={team} />
      </Section>
    </div>
  );
}

function Section({
  icon,
  title,
  count,
  open,
  onToggle,
  children,
}: {
  icon: ReactNode;
  title: string;
  count?: number;
  open: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  return (
    <section className={`rsec${open ? " open" : ""}`}>
      <header onClick={onToggle}>
        {icon}
        <span className="rp-title">{title}</span>
        {count !== undefined && <span className="rp-count">{count}</span>}
        <ChevronIcon open={open} />
      </header>
      {open && <div className="rp-body">{children}</div>}
    </section>
  );
}
