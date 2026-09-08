import type { Effort } from "../types";

export function formatEffort(e: Effort | null): string {
  if (!e) return "—";
  const unit = e.unit.toLowerCase();
  const singular = e.value === 1 ? unit.replace(/s$/, "") : unit;
  return `${e.value} ${singular}`;
}

export function formatDate(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export function formatDateTime(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function daysUntil(iso: string | null): number | null {
  if (!iso) return null;
  const ms = new Date(iso).getTime() - Date.now();
  return Math.round(ms / 86_400_000);
}

export function dueLabel(iso: string | null): string {
  const d = daysUntil(iso);
  if (d === null) return "";
  if (d < 0) return `${Math.abs(d)}d overdue`;
  if (d === 0) return "due today";
  return `in ${d}d`;
}

export function score(n: number): string {
  return n.toFixed(3);
}
