import { useConfig } from "../config/ConfigContext";

type Shape = "critical" | "high" | "medium" | "low";
const VAR: Record<Shape, string> = {
  critical: "var(--p-critical)",
  high: "var(--p-high)",
  medium: "var(--p-medium)",
  low: "var(--p-low)",
};

function ratioOf(priority: string, values: Record<string, number> | undefined): number {
  if (!values || Object.keys(values).length === 0) return 0.5;
  const v = values[priority];
  if (v === undefined) return 0.5;
  const max = Math.max(...Object.values(values));
  return max === 0 ? 0 : v / max;
}
function shapeFor(r: number): Shape {
  if (r >= 0.9) return "critical";
  if (r >= 0.6) return "high";
  if (r >= 0.35) return "medium";
  return "low";
}

function Glyph({ shape }: { shape: Shape }) {
  const p = {
    width: 13,
    height: 13,
    viewBox: "0 0 14 14",
    fill: "none",
    stroke: VAR[shape],
    strokeWidth: 1.9,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
  };
  switch (shape) {
    case "critical":
      return (
        <svg {...p}>
          <path d="M3 6.5 L7 3 L11 6.5" />
          <path d="M3 11 L7 7.5 L11 11" />
        </svg>
      );
    case "high":
      return (
        <svg {...p}>
          <path d="M3 9 L7 5 L11 9" />
        </svg>
      );
    case "medium":
      return (
        <svg {...p}>
          <path d="M3 5.5 H11" />
          <path d="M3 9 H11" />
        </svg>
      );
    case "low":
      return (
        <svg {...p}>
          <path d="M3 5.5 L7 9.5 L11 5.5" />
        </svg>
      );
  }
}

export function PriorityMark({ priority }: { priority: string }) {
  const config = useConfig();
  const shape = shapeFor(ratioOf(priority, config?.priorityValues));
  return (
    <span className="pmark" title={priority}>
      <Glyph shape={shape} />
    </span>
  );
}
