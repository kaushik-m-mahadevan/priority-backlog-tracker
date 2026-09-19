import { afterEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { useMyNeedleInventory } from "./useMyNeedleInventory";
import { api } from "../api/client";
import { materialInventoryApi } from "../materialinventory/api";
import type { NeedleInventoryEntryView } from "../materialinventory/types";

vi.mock("../api/client", () => ({
  api: { get: vi.fn() },
}));

vi.mock("../materialinventory/api", () => ({
  materialInventoryApi: { myInventory: vi.fn(), myNeedleInventory: vi.fn() },
}));

function entry(overrides: Partial<NeedleInventoryEntryView> = {}): NeedleInventoryEntryView {
  return { id: "e1", userId: "u1", needleTypeId: "n1", quantity: 2, updatedAt: "2026-01-01T00:00:00Z", ...overrides };
}

describe("useMyNeedleInventory", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("returns an empty map with no API calls when there is no group", () => {
    const { result } = renderHook(() => useMyNeedleInventory(null));

    expect(result.current).toEqual(new Map());
    expect(api.get).not.toHaveBeenCalled();
  });

  it("returns an empty map when there is no linked material-inventory group", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: null });

    const { result } = renderHook(() => useMyNeedleInventory("g1"));

    await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(result.current).toEqual(new Map());
    expect(materialInventoryApi.myNeedleInventory).not.toHaveBeenCalled();
  });

  it("builds a Map keyed by needleTypeId to quantity from the linked group's inventory", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.myNeedleInventory).mockResolvedValue([
      entry({ needleTypeId: "n1", quantity: 4 }),
      entry({ id: "e2", needleTypeId: "n2", quantity: 1 }),
    ]);

    const { result } = renderHook(() => useMyNeedleInventory("g1"));

    await waitFor(() => expect(result.current.size).toBe(2));
    expect(result.current.get("n1")).toBe(4);
    expect(result.current.get("n2")).toBe(1);
  });

  it("falls back to an empty map when the link lookup rejects", async () => {
    vi.mocked(api.get).mockRejectedValue(new Error("network error"));

    const { result } = renderHook(() => useMyNeedleInventory("g1"));

    await waitFor(() => expect(result.current).toEqual(new Map()));
  });

  it("falls back to an empty map when fetching inventory itself rejects", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.myNeedleInventory).mockRejectedValue(new Error("500"));

    const { result } = renderHook(() => useMyNeedleInventory("g1"));

    await waitFor(() => expect(result.current).toEqual(new Map()));
  });

  it("re-fetches when groupId changes", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.myNeedleInventory).mockResolvedValue([entry({ needleTypeId: "n1", quantity: 4 })]);

    const { result, rerender } = renderHook(({ groupId }) => useMyNeedleInventory(groupId), {
      initialProps: { groupId: "g1" as string | null },
    });
    await waitFor(() => expect(result.current.get("n1")).toBe(4));

    vi.mocked(materialInventoryApi.myNeedleInventory).mockResolvedValue([entry({ needleTypeId: "n3", quantity: 7 })]);
    rerender({ groupId: "g2" });

    await waitFor(() => expect(result.current.get("n3")).toBe(7));
    expect(api.get).toHaveBeenCalledWith(expect.stringContaining("g2"));
  });
});
