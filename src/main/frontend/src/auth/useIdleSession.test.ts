import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useIdleSession } from "./useIdleSession";

const MIN = 60_000;

describe("useIdleSession", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function setup(timeoutMinutes: number | null = 10, enabled = true) {
    const onIdle = vi.fn();
    const onHeartbeat = vi.fn();
    renderHook(() => useIdleSession({ enabled, timeoutMinutes, onIdle, onHeartbeat }));
    return { onIdle, onHeartbeat };
  }

  it("fires onIdle once there has been no interaction for the timeout", () => {
    const { onIdle } = setup(10);
    act(() => vi.advanceTimersByTime(9 * MIN));
    expect(onIdle).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(MIN + 15_000));
    expect(onIdle).toHaveBeenCalled();
  });

  it("interaction restarts the countdown", () => {
    const { onIdle } = setup(10);
    act(() => vi.advanceTimersByTime(8 * MIN));
    act(() => window.dispatchEvent(new Event("keydown")));
    act(() => vi.advanceTimersByTime(8 * MIN));
    expect(onIdle).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(3 * MIN));
    expect(onIdle).toHaveBeenCalled();
  });

  it("sends a heartbeat when the user was active, at most about once a minute", () => {
    const { onHeartbeat } = setup(10);
    act(() => vi.advanceTimersByTime(2 * MIN));
    expect(onHeartbeat).not.toHaveBeenCalled(); // nobody did anything

    act(() => window.dispatchEvent(new Event("pointerdown")));
    act(() => vi.advanceTimersByTime(15_000));
    expect(onHeartbeat).toHaveBeenCalledTimes(1);

    act(() => window.dispatchEvent(new Event("pointerdown")));
    act(() => vi.advanceTimersByTime(15_000));
    expect(onHeartbeat).toHaveBeenCalledTimes(1); // throttled
    act(() => vi.advanceTimersByTime(MIN));
    expect(onHeartbeat).toHaveBeenCalledTimes(2);
  });

  it("does nothing when disabled or when no timeout is known yet", () => {
    const disabled = setup(10, false);
    const unknown = setup(null);
    act(() => vi.advanceTimersByTime(30 * MIN));
    expect(disabled.onIdle).not.toHaveBeenCalled();
    expect(unknown.onIdle).not.toHaveBeenCalled();
  });
});
