import type { Effort } from "../types";

/* ---- effort ------------------------------------------------------------------ */

/** A human span rather than a measurement (design §14 allows 15/30/45m, 1-23h, 1-30d). */
export function effortSpan(e: Effort | null): string {
  if (!e) return "unsized";
  const m = e.minutes;
  if (m <= 45) return "quick fix";
  if (m <= 180) return "an hour or two";
  if (m <= 480) return "an afternoon";
  if (m <= 1440) return "a full day";
  if (m <= 2880) return "a couple of days";
  if (m <= 7200) return "a few days";
  if (m <= 14400) return "about a week";
  return "a big one";
}

/** 0..1 fill for the weight bar. Soft-capped at ~10 working days so the bar has range. */
export function effortWeight(e: Effort | null): number {
  if (!e) return 0;
  const cap = 14400; // 10 days
  return Math.min(e.minutes, cap) / cap;
}

/* ---- dates ----------------------------------------------------------------- */

export function daysUntil(iso: string | null): number | null {
  if (!iso) return null;
  const a = new Date(iso);
  a.setHours(0, 0, 0, 0);
  const b = new Date();
  b.setHours(0, 0, 0, 0);
  return Math.round((a.getTime() - b.getTime()) / 86_400_000);
}

export type DueTone = "late" | "soon" | "normal" | "far";

export interface DueChip {
  text: string;
  tone: DueTone;
}

const WEEKDAY = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTH = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** Short, verbal up close; muted and coarse far away (Todoist / Things style). */
export function dueChip(iso: string | null): DueChip {
  const d = daysUntil(iso);
  if (d === null) return { text: "no date", tone: "far" };
  if (d < 0) return { text: d === -1 ? "yesterday" : `${-d}d late`, tone: "late" };
  if (d === 0) return { text: "today", tone: "soon" };
  if (d === 1) return { text: "tomorrow", tone: "soon" };
  if (d <= 6) return { text: WEEKDAY[new Date(iso!).getDay()], tone: "normal" };
  if (d <= 13) return { text: "1 wk", tone: "far" };
  if (d <= 34) return { text: `${Math.round(d / 7)} wk`, tone: "far" };
  return { text: MONTH[new Date(iso!).getMonth()], tone: "far" };
}

export function formatDate(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
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

export function score(n: number): string {
  return n.toFixed(3);
}
