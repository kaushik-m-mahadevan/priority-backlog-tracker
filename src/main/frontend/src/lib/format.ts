import type { Effort } from "../types";
import { displayTz } from "./tz";

/* ---- effort ---------------------------------------------------------------- */

export type GrowthStage = 0 | 1 | 2 | 3 | 4 | 5;

/** Map an effort estimate to a nature-growth stage (design §14). */
export function growthStage(e: Effort | null): GrowthStage {
  if (!e) return 0;
  const m = e.minutes;
  if (m <= 45) return 0; // seedling
  if (m <= 240) return 1; // sprout      (<= 4h)
  if (m <= 480) return 2; // branch      (<= 8h)
  if (m <= 1440) return 3; // young tree (<= 1d)
  if (m <= 5760) return 4; // tree       (<= 4d)
  return 5; // grove / hill
}

const SPAN = [
  "quick — under an hour",
  "short — a few hours",
  "half a day",
  "about a day",
  "a few days",
  "a big one — a week or more",
];

export function effortLabel(e: Effort | null): string {
  return e ? SPAN[growthStage(e)] : "unsized";
}

/* ---- dates -------------------------------------------------------------- */

/** Calendar date (YYYY-MM-DD) of an instant, read in the chosen display timezone. */
function localDay(d: Date): number {
  const s = new Intl.DateTimeFormat("en-CA", {
    timeZone: displayTz(),
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(d);
  return Date.parse(s + "T00:00:00Z");
}

export function daysUntil(iso: string | null): number | null {
  if (!iso) return null;
  return Math.round((localDay(new Date(iso)) - localDay(new Date())) / 86_400_000);
}

export interface Due {
  /** one compact unit, e.g. "3d", "6w", "2mo" — always present */
  text: string;
  /** overdue or due today */
  urgent: boolean;
}

/** One unit only. Red "!" is driven by `urgent` (design: keep it simple). */
export function due(iso: string | null): Due {
  const d = daysUntil(iso);
  if (d === null) return { text: "—", urgent: false };
  const mag = Math.abs(d);
  let text: string;
  if (mag === 0) text = "0d";
  else if (mag < 7) text = `${mag}d`;
  else if (mag < 60) text = `${Math.round(mag / 7)}w`;
  else text = `${Math.round(mag / 30)}mo`;
  return { text, urgent: d <= 0 };
}

export function formatDate(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString(undefined, {
    timeZone: displayTz(),
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function formatDateTime(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString(undefined, {
    timeZone: displayTz(),
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** "5d", "3w", "2mo" — bare magnitude, for ages in Needs Attention. */
export function ageShort(days: number): string {
  if (days < 14) return `${days}d`;
  if (days < 60) return `${Math.round(days / 7)}w`;
  return `${Math.round(days / 30)}mo`;
}

export function score(n: number): string {
  return n.toFixed(3);
}
