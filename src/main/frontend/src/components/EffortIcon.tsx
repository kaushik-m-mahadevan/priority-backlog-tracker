import { growthStage, effortLabel } from "../lib/format";
import type { Effort } from "../types";

/* Nature-growth scale as solid minimalist glyphs (no outlines):
   seed -> sprout -> young tree -> tree -> full tree -> grove. */
const STAGES = [
  // 0 seed + first leaf
  <g key="0">
    <circle cx="12" cy="18.5" r="2" />
    <path d="M12 17c0-3 2.4-4.8 4.2-5.2C15.9 14.4 14.2 17 12 17Z" />
  </g>,
  // 1 sprout — two leaves on a stem
  <g key="1">
    <rect x="11.2" y="11" width="1.6" height="9.5" rx="0.8" />
    <path d="M12 13c-2.6 0-4.2-2.6-4.2-2.6C10.4 10.4 12 11.7 12 13Z" />
    <path d="M12 13c2.6 0 4.2-2.6 4.2-2.6C13.6 10.4 12 11.7 12 13Z" />
  </g>,
  // 2 young tree
  <g key="2">
    <rect x="11" y="13" width="2" height="7.5" rx="1" />
    <circle cx="12" cy="9.5" r="4.2" />
  </g>,
  // 3 tree
  <g key="3">
    <rect x="10.8" y="14" width="2.4" height="6.5" rx="1.2" />
    <circle cx="12" cy="9" r="5.4" />
  </g>,
  // 4 full tree
  <g key="4">
    <rect x="10.8" y="15" width="2.4" height="5.5" rx="1.2" />
    <circle cx="12" cy="8.5" r="5" />
    <circle cx="7.6" cy="11.5" r="3.2" />
    <circle cx="16.4" cy="11.5" r="3.2" />
  </g>,
  // 5 grove
  <g key="5">
    <rect x="11.2" y="15" width="1.8" height="5.5" rx="0.9" />
    <rect x="5.7" y="17" width="1.5" height="3.5" rx="0.75" />
    <rect x="16.8" y="17" width="1.5" height="3.5" rx="0.75" />
    <circle cx="12" cy="10" r="4.6" />
    <circle cx="6.4" cy="13.5" r="3.2" />
    <circle cx="17.6" cy="13.5" r="3.2" />
  </g>,
];

export function EffortIcon({ effort, size = 18 }: { effort: Effort | null; size?: number }) {
  const stage = growthStage(effort);
  return (
    <span className="eff" title={effortLabel(effort)} aria-label={effortLabel(effort)}>
      <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        {STAGES[stage]}
      </svg>
    </span>
  );
}
