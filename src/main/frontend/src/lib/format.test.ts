import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ageShort, due, effortLabel, growthStage, daysUntil } from "./format";
import type { Effort } from "../types";

const eff = (minutes: number): Effort => ({ value: minutes, unit: "MINUTES", minutes });

describe("growthStage", () => {
  it("maps effort minutes to the six nature stages (§14)", () => {
    expect(growthStage(null)).toBe(0);
    expect(growthStage(eff(45))).toBe(0);
    expect(growthStage(eff(46))).toBe(1);
    expect(growthStage(eff(240))).toBe(1);
    expect(growthStage(eff(480))).toBe(2);
    expect(growthStage(eff(1440))).toBe(3);
    expect(growthStage(eff(5760))).toBe(4);
    expect(growthStage(eff(5761))).toBe(5);
  });
});

describe("effortLabel", () => {
  it("gives a natural span, or 'unsized' when absent", () => {
    expect(effortLabel(null)).toBe("unsized");
    expect(effortLabel(eff(30))).toBe("quick — under an hour");
    expect(effortLabel(eff(10000))).toBe("a big one — a week or more");
  });
});

describe("ageShort", () => {
  it("collapses to one unit at the 14d and 60d thresholds", () => {
    expect(ageShort(3)).toBe("3d");
    expect(ageShort(13)).toBe("13d");
    expect(ageShort(21)).toBe("3w");
    expect(ageShort(60)).toBe("2mo");
  });
});

describe("due / daysUntil", () => {
  // pin the display zone so calendar-day math is deterministic on any host
  beforeEach(() => vi.stubGlobal("localStorage", { getItem: () => "UTC", setItem: () => {} }));
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("is '—' and not urgent when there is no date", () => {
    expect(due(null)).toEqual({ text: "—", urgent: false });
    expect(daysUntil(null)).toBeNull();
  });

  it("counts calendar days and flags today-or-past as urgent", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-09-08T09:00:00Z"));
    expect(daysUntil("2026-09-11T23:00:00Z")).toBe(3);
    expect(due("2026-09-11T23:00:00Z")).toEqual({ text: "3d", urgent: false });
    expect(due("2026-09-08T23:00:00Z")).toEqual({ text: "0d", urgent: true });
    expect(due("2026-09-01T00:00:00Z").urgent).toBe(true);
    expect(due("2026-11-20T00:00:00Z").text).toBe("2mo");
  });
});
