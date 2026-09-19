import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { useKeepAlive } from "./useKeepAlive";

describe("useKeepAlive", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  function stubVisibility(state: DocumentVisibilityState) {
    vi.spyOn(document, "visibilityState", "get").mockReturnValue(state);
  }

  it("pings /actuator/health immediately on mount when the tab is visible", () => {
    stubVisibility("visible");

    renderHook(() => useKeepAlive());

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch).toHaveBeenCalledWith("/actuator/health", { method: "GET", cache: "no-store" });
  });

  it("does not fetch at all when the tab is hidden", () => {
    stubVisibility("hidden");

    renderHook(() => useKeepAlive());

    expect(fetch).not.toHaveBeenCalled();
  });

  it("pings again after the interval elapses while visible", () => {
    stubVisibility("visible");

    renderHook(() => useKeepAlive());
    expect(fetch).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(4 * 60 * 1000);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("skips the interval ping (but not the initial one) when the tab went hidden meanwhile", () => {
    stubVisibility("visible");
    renderHook(() => useKeepAlive());
    expect(fetch).toHaveBeenCalledTimes(1);

    stubVisibility("hidden");
    vi.advanceTimersByTime(4 * 60 * 1000);
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("clears its interval on unmount", () => {
    stubVisibility("visible");
    const clearSpy = vi.spyOn(window, "clearInterval");

    const { unmount } = renderHook(() => useKeepAlive());
    unmount();

    expect(clearSpy).toHaveBeenCalled();
  });

  it("never throws when the fetch itself rejects (offline / backend down)", async () => {
    stubVisibility("visible");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network down")));

    expect(() => renderHook(() => useKeepAlive())).not.toThrow();
  });
});
