import { useConfig } from "../config/ConfigContext";

type Shape = "critical" | "high" | "medium" | "low";

const COLOR: Record<Shape, string> = {
  critical: "#ef4444",
  high: "#f59e0b",
  medium: "#3b82f6",
  low: "#22c55e",
};

/** Rank a priority label 0..1 against the configured priorityValues (design §3). */
function ratioOf(priority: string, values: Record<string, number> | undefined): number {
  if (!values || Object.keys(values).length === 0) return 0.5;
  const v = values[priority];
  if (v === undefined) return 0.5;
  const max = Math.max(...Object.values(values));
  return max === 0 ? 0 : v / max;
}

function shapeFor(ratio: number): Shape {
  if (ratio >= 0.9) return "critical";
  if (ratio >= 0.6) return "high";
  if (ratio >= 0.35) return "medium";
  return "low";
}

function Glyph({ shape, color }: { shape: Shape; color: string }) {
  const common = {
    width: 14,
    height: 14,
    viewBox: "0 0 14 14",
    fill: "none",
    stroke: color,
    strokeWidth: 2,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
  };
  switch (shape) {
    case "critical":
      return (
        <svg {...common} aria-hidden="true">
          <path d="M3 6.5 L7 3 L11 6.5" />
          <path d="M3 11 L7 7.5 L11 11" />
        </svg>
      );
    case "high":
      return (
        <svg {...common} aria-hidden="true">
          <path d="M3 9 L7 5 L11 9" />
        </svg>
      );
    case "medium":
      return (
        <svg {...common} aria-hidden="true">
          <path d="M3 5.5 H11" />
          <path d="M3 9 H11" />
        </svg>
      );
    case "low":
      return (
        <svg {...common} aria-hidden="true">
          <path d="M3 5.5 L7 9.5 L11 5.5" />
        </svg>
      );
  }
}

/** Jira-style priority arrow. Double-up = highest, down = lowest. */
export function PriorityMark({ priority }: { priority: string }) {
  const config = useConfig();
  const shape = shapeFor(ratioOf(priority, config?.priorityValues));
  return (
    <span
      title={priority}
      style={{ display: "inline-flex", alignItems: "center", flexShrink: 0 }}
    >
      <Glyph shape={shape} color={COLOR[shape]} />
    </span>
  );
}
