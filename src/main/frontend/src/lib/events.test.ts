import { afterEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { notifyItemsChanged, useItemsChanged } from "./events";

describe("useItemsChanged / notifyItemsChanged", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("calls the callback whenever notifyItemsChanged() fires", () => {
    const cb = vi.fn();
    renderHook(() => useItemsChanged(cb));

    notifyItemsChanged();

    expect(cb).toHaveBeenCalledTimes(1);
  });

  it("does not call the callback for unrelated window events", () => {
    const cb = vi.fn();
    renderHook(() => useItemsChanged(cb));

    window.dispatchEvent(new Event("some-other-event"));

    expect(cb).not.toHaveBeenCalled();
  });

  it("always invokes the latest callback without re-subscribing on every render", () => {
    const first = vi.fn();
    const second = vi.fn();
    const addSpy = vi.spyOn(window, "addEventListener");

    const { rerender } = renderHook(({ cb }) => useItemsChanged(cb), {
      initialProps: { cb: first },
    });
    const itemsChangedSubscriptions = addSpy.mock.calls.filter(([type]) => type === "pbt:items-changed");
    expect(itemsChangedSubscriptions).toHaveLength(1);

    rerender({ cb: second });
    // still only one subscription — the ref pattern updates in place, no new listener
    const stillOne = addSpy.mock.calls.filter(([type]) => type === "pbt:items-changed");
    expect(stillOne).toHaveLength(1);

    notifyItemsChanged();

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledTimes(1);
  });

  it("removes the window listener on unmount", () => {
    const removeSpy = vi.spyOn(window, "removeEventListener");
    const cb = vi.fn();

    const { unmount } = renderHook(() => useItemsChanged(cb));
    unmount();

    expect(removeSpy).toHaveBeenCalledWith("pbt:items-changed", expect.any(Function));

    notifyItemsChanged();
    expect(cb).not.toHaveBeenCalled();
  });
});
