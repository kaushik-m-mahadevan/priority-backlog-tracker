import { growthStage, effortLabel } from "../lib/format";
import type { Effort } from "../types";

/* "Leaf count" growth scale — a sprig that gains leaves as the effort grows.
   Solid fill, one colour, no canopy blob. 6 stages from growthStage(). */
const STAGES = [
  <g key="0">
    <path d="M15.4 27c.3-9 .6-12 .6-12s.3 3 .6 12Z" />
    <path d="M16 17c3-.2 4.6-2.6 4.6-2.6-3-1-4.6.3-4.6 2.6Z" />
  </g>,
  <g key="1">
    <path d="M15.4 27c.3-11 .6-15 .6-15s.3 4 .6 15Z" />
    <path d="M16 20c-3-.2-4.6-2.6-4.6-2.6C14.4 16.4 16 17.7 16 20Z" />
    <path d="M16 15c3-.2 4.6-2.6 4.6-2.6C17.6 11.4 16 12.7 16 15Z" />
  </g>,
  <g key="2">
    <path d="M15.4 27c.3-14 .6-18 .6-18s.3 4 .6 18Z" />
    <path d="M16 22c-3-.2-4.6-2.6-4.6-2.6C14.4 18.4 16 19.7 16 22Z" />
    <path d="M16 17c3-.2 4.6-2.6 4.6-2.6C17.6 13.4 16 14.7 16 17Z" />
    <path d="M16 12c-3-.2-4.6-2.6-4.6-2.6C14.4 8.4 16 9.7 16 12Z" />
  </g>,
  <g key="3">
    <path d="M15.4 27c.3-16 .6-20 .6-20s.3 4 .6 20Z" />
    <path d="M16 23c-3.2-.2-4.9-2.7-4.9-2.7 3.2-1.1 4.9.3 4.9 2.7Z" />
    <path d="M16 18c3.2-.2 4.9-2.7 4.9-2.7-3.2-1.1-4.9.3-4.9 2.7Z" />
    <path d="M16 13c-3.2-.2-4.9-2.7-4.9-2.7 3.2-1.1 4.9.3 4.9 2.7Z" />
    <path d="M16 9c3-.2 4.6-2.5 4.6-2.5-3-1-4.6.2-4.6 2.5Z" />
  </g>,
  <g key="4">
    <path d="M15.3 27c.4-19 .7-23 .7-23s.3 4 .7 23Z" />
    <path d="M16 24c-3.4-.2-5.2-2.8-5.2-2.8 3.4-1.2 5.2.3 5.2 2.8Z" />
    <path d="M16 19.5c3.4-.2 5.2-2.8 5.2-2.8-3.4-1.2-5.2.3-5.2 2.8Z" />
    <path d="M16 15c-3.4-.2-5.2-2.8-5.2-2.8 3.4-1.2 5.2.3 5.2 2.8Z" />
    <path d="M16 10.5c3.4-.2 5.2-2.8 5.2-2.8-3.4-1.2-5.2.3-5.2 2.8Z" />
    <path d="M16 6.5c-2.8-.2-4.3-2.3-4.3-2.3 2.8-1 4.3.2 4.3 2.3Z" />
  </g>,
  <g key="5">
    <path d="M15.3 27c.4-20 .7-24 .7-24s.3 4 .7 24Z" />
    <path d="M16 24c-3.6-.2-5.5-3-5.5-3 3.6-1.3 5.5.3 5.5 3Z" />
    <path d="M16 24c3.6-.2 5.5-3 5.5-3-3.6-1.3-5.5.3-5.5 3Z" />
    <path d="M16 18.5c-3.6-.2-5.5-3-5.5-3 3.6-1.3 5.5.3 5.5 3Z" />
    <path d="M16 18.5c3.6-.2 5.5-3 5.5-3-3.6-1.3-5.5.3-5.5 3Z" />
    <path d="M16 13c-3.4-.2-5.2-2.8-5.2-2.8 3.4-1.2 5.2.3 5.2 2.8Z" />
    <path d="M16 13c3.4-.2 5.2-2.8 5.2-2.8-3.4-1.2-5.2.3-5.2 2.8Z" />
    <path d="M16 8c-2.8-.2-4.3-2.3-4.3-2.3 2.8-1 4.3.2 4.3 2.3Z" />
  </g>,
];

export function EffortIcon({ effort, size = 18 }: { effort: Effort | null; size?: number }) {
  const stage = growthStage(effort);
  return (
    <span className="eff" title={effortLabel(effort)} aria-label={effortLabel(effort)}>
      <svg width={size} height={size} viewBox="0 0 32 32" fill="currentColor" aria-hidden="true">
        {STAGES[stage]}
      </svg>
    </span>
  );
}
