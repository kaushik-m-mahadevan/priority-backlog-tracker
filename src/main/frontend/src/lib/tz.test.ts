import { afterEach, describe, expect, it, vi } from "vitest";
import { DEFAULT_TZ, displayTz, getTzPref } from "./tz";

function withStore(value: string | null) {
  vi.stubGlobal("localStorage", {
    getItem: () => value,
    setItem: () => {},
  });
}

describe("tz preference", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("defaults to IST when nothing is stored", () => {
    withStore(null);
    expect(getTzPref()).toBe(DEFAULT_TZ);
    expect(displayTz()).toBe("Asia/Kolkata");
  });

  it("returns undefined (runtime zone) for the 'system' choice", () => {
    withStore("system");
    expect(displayTz()).toBeUndefined();
  });

  it("passes a stored IANA zone straight through", () => {
    withStore("America/New_York");
    expect(displayTz()).toBe("America/New_York");
  });

  it("falls back to the default if localStorage throws", () => {
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw new Error("blocked");
      },
    });
    expect(getTzPref()).toBe(DEFAULT_TZ);
  });
});
