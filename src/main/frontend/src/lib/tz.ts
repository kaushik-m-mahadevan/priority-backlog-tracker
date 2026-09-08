/**
 * Display timezone (design §22). The backend stores every instant in UTC; which wall
 * clock the founders read it in is a per-browser preference. Defaults to IST.
 *
 * Stored in localStorage as an IANA zone id, or the literal "system" to follow the
 * browser. Changing it fires a `pbt:tz` window event so open views re-render.
 */
const KEY = "pbt.tz";
export const DEFAULT_TZ = "Asia/Kolkata";
export const TZ_EVENT = "pbt:tz";

/** A short, deliberately small menu — the team is in three places, not thirty. */
export const TZ_CHOICES: { id: string; label: string }[] = [
  { id: "system", label: "System default" },
  { id: "Asia/Kolkata", label: "India — IST" },
  { id: "Europe/London", label: "UK — GMT/BST" },
  { id: "America/New_York", label: "US East — ET" },
  { id: "America/Los_Angeles", label: "US West — PT" },
  { id: "UTC", label: "UTC" },
];

export function getTzPref(): string {
  try {
    return localStorage.getItem(KEY) ?? DEFAULT_TZ;
  } catch {
    return DEFAULT_TZ;
  }
}

/** The value to hand `Intl` — `undefined` means "let the runtime use the system zone". */
export function displayTz(): string | undefined {
  const p = getTzPref();
  return p === "system" ? undefined : p;
}

export function setTzPref(value: string): void {
  try {
    localStorage.setItem(KEY, value);
  } catch {
    /* private mode — the preference just won't stick */
  }
  window.dispatchEvent(new Event(TZ_EVENT));
}
