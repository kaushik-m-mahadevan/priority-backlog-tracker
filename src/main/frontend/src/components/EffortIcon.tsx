import { growthStage, effortLabel } from "../lib/format";
import type { Effort } from "../types";

/* "Leaf count" growth scale — a sprig that gains broad leaves as the effort grows.
   Solid fill, one colour, chunky enough to read at row size. 6 stages. */

function Stem({ top }: { top: number }) {
  return <rect x="11.1" y={top} width="1.8" height={22 - top} rx="0.9" />;
}
function Leaf({ x, y, dir }: { x: number; y: number; dir: 1 | -1 }) {
  return (
    <ellipse
      cx={x}
      cy={y}
      rx="4.3"
      ry="2.3"
      transform={`rotate(${dir === 1 ? -34 : 34} ${x} ${y})`}
    />
  );
}

const STAGES = [
  <g key="0">
    <Stem top={13} />
    <circle cx="12" cy="20" r="1.9" />
    <Leaf x={14.6} y={12} dir={1} />
  </g>,
  <g key="1">
    <Stem top={11} />
    <Leaf x={14.6} y={17} dir={1} />
    <Leaf x={9.4} y={13} dir={-1} />
  </g>,
  <g key="2">
    <Stem top={8} />
    <Leaf x={14.6} y={19} dir={1} />
    <Leaf x={9.4} y={15} dir={-1} />
    <Leaf x={14.6} y={11} dir={1} />
  </g>,
  <g key="3">
    <Stem top={6} />
    <Leaf x={14.6} y={20} dir={1} />
    <Leaf x={9.4} y={16} dir={-1} />
    <Leaf x={14.6} y={12} dir={1} />
    <Leaf x={9.4} y={9} dir={-1} />
  </g>,
  <g key="4">
    <Stem top={4} />
    <Leaf x={14.7} y={20.5} dir={1} />
    <Leaf x={9.3} y={17} dir={-1} />
    <Leaf x={14.7} y={13.5} dir={1} />
    <Leaf x={9.3} y={10} dir={-1} />
    <Leaf x={12.6} y={6.5} dir={1} />
  </g>,
  <g key="5">
    <Stem top={3} />
    <Leaf x={15} y={19} dir={1} />
    <Leaf x={9} y={19} dir={-1} />
    <Leaf x={15} y={13.5} dir={1} />
    <Leaf x={9} y={13.5} dir={-1} />
    <Leaf x={14.6} y={8.5} dir={1} />
    <Leaf x={9.4} y={8.5} dir={-1} />
  </g>,
];

export function EffortIcon({ effort, size = 22 }: { effort: Effort | null; size?: number }) {
  const stage = growthStage(effort);
  return (
    <span className="eff" title={effortLabel(effort)} aria-label={effortLabel(effort)}>
      <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        {STAGES[stage]}
      </svg>
    </span>
  );
}
