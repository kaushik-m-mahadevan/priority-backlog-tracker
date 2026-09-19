import { afterEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { GroupProvider, useGroups } from "./GroupContext";
import { api } from "../api/client";
import { ITEMS_CHANGED } from "../lib/events";
import type { GroupView } from "../types";

vi.mock("../api/client", () => ({
  api: { get: vi.fn() },
}));

function group(overrides: Partial<GroupView> = {}): GroupView {
  return { id: "g1", name: "Household", createdAt: "2026-01-01T00:00:00Z", members: [], ...overrides };
}

describe("useGroups", () => {
  afterEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("throws when used outside a GroupProvider", () => {
    expect(() => renderHook(() => useGroups())).toThrow("useGroups must be used within GroupProvider");
  });

  it("loads the groups and defaults to the first one", async () => {
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), group({ id: "g2" })]);

    const { result } = renderHook(() => useGroups(), { wrapper: GroupProvider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(api.get).toHaveBeenCalledWith("/groups");
    expect(result.current.currentGroupId).toBe("g1");
  });

  it("keeps a previously-stored selection if it is still in the list", async () => {
    localStorage.setItem("pbt.groupId", "g2");
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), group({ id: "g2" })]);

    const { result } = renderHook(() => useGroups(), { wrapper: GroupProvider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.currentGroupId).toBe("g2");
  });

  it("falls back to the first group when the stored selection is no longer in the list", async () => {
    localStorage.setItem("pbt.groupId", "gone");
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), group({ id: "g2" })]);

    const { result } = renderHook(() => useGroups(), { wrapper: GroupProvider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.currentGroupId).toBe("g1");
  });

  it("clears the list and stops loading when the fetch rejects", async () => {
    vi.mocked(api.get).mockRejectedValue(new Error("network error"));

    const { result } = renderHook(() => useGroups(), { wrapper: GroupProvider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.groups).toEqual([]);
    expect(result.current.currentGroupId).toBeNull();
  });

  it("setCurrentGroup() updates selection, persists it, and broadcasts ITEMS_CHANGED", async () => {
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), group({ id: "g2" })]);
    const { result } = renderHook(() => useGroups(), { wrapper: GroupProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    const listener = vi.fn();
    window.addEventListener(ITEMS_CHANGED, listener);

    act(() => {
      result.current.setCurrentGroup("g2");
    });

    expect(result.current.currentGroupId).toBe("g2");
    expect(localStorage.getItem("pbt.groupId")).toBe("g2");
    expect(listener).toHaveBeenCalledTimes(1);

    window.removeEventListener(ITEMS_CHANGED, listener);
  });
});
