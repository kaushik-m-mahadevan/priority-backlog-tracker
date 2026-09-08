import { growthStage, effortLabel } from "../lib/format";
import type { Effort } from "../types";

/* Nature-growth scale: seedling -> sprout -> branch -> young tree -> tree -> grove. */
const STAGES = [
  // 0 seedling
  <g key="0">
    <path d="M12 21 V13" />
    <path d="M12 13 C9 12 8 9 8 9 C11 9 12 12 12 13Z" />
  </g>,
  // 1 sprout (two leaves)
  <g key="1">
    <path d="M12 21 V11" />
    <path d="M12 12 C9 12 7.5 9 7.5 9 C10.5 9 12 11 12 12Z" />
    <path d="M12 12 C15 12 16.5 9 16.5 9 C13.5 9 12 11 12 12Z" />
  </g>,
  // 2 branch
  <g key="2">
    <path d="M12 21 V8" />
    <path d="M12 14 L8.5 10.5 M12 12 L15.5 8.5 M12 16 L15 13.5" />
  </g>,
  // 3 young tree
  <g key="3">
    <path d="M12 21 V13" />
    <circle cx="12" cy="9" r="4.2" />
  </g>,
  // 4 tree
  <g key="4">
    <path d="M12 21 V14" />
    <circle cx="12" cy="8.5" r="5" />
    <circle cx="8" cy="11" r="3" />
    <circle cx="16" cy="11" r="3" />
  </g>,
  // 5 grove / hill
  <g key="5">
    <path d="M12 21 V15 M8 21 V17 M16 21 V17" />
    <circle cx="12" cy="10" r="5" />
    <circle cx="7" cy="13.5" r="3.2" />
    <circle cx="17" cy="13.5" r="3.2" />
  </g>,
];

export function EffortIcon({ effort, size = 18 }: { effort: Effort | null; size?: number }) {
  const stage = growthStage(effort);
  return (
    <span className="eff" title={effortLabel(effort)} aria-label={effortLabel(effort)}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.6}
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        {STAGES[stage]}
      </svg>
    </span>
  );
}
