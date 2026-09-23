import { afterEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { useMyYarnInventory } from "./useMyYarnInventory";
import { api } from "../api/client";
import { materialInventoryApi } from "../materialinventory/api";
import type { InventoryEntryView } from "../materialinventory/types";

vi.mock("../api/client", () => ({
  api: { get: vi.fn() },
}));

vi.mock("../materialinventory/api", () => ({
  materialInventoryApi: { myInventory: vi.fn(), myNeedleInventory: vi.fn() },
}));

function entry(overrides: Partial<InventoryEntryView> = {}): InventoryEntryView {
  return {
    id: "e1", userId: "u1", yarnTypeId: "y1", quantity: 3, updatedAt: "2026-01-01T00:00:00Z", stale: false,
    reserved: 0, available: 3, personalLow: false, businessLow: false,
    ...overrides,
  };
}

describe("useMyYarnInventory", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("returns an empty map with no API calls when there is no group", () => {
    const { result } = renderHook(() => useMyYarnInventory(null));

    expect(result.current).toEqual(new Map());
    expect(api.get).not.toHaveBeenCalled();
  });

  it("returns an empty map when there is no linked material-inventory group", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: null });

    const { result } = renderHook(() => useMyYarnInventory("g1"));

    await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(result.current).toEqual(new Map());
    expect(materialInventoryApi.myInventory).not.toHaveBeenCalled();
  });

  it("builds a Map keyed by yarnTypeId to quantity from the linked group's inventory", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.myInventory).mockResolvedValue([
      entry({ yarnTypeId: "y1", quantity: 5 }),
      entry({ id: "e2", yarnTypeId: "y2", quantity: 10 }),
    ]);

    const { result } = renderHook(() => useMyYarnInventory("g1"));

    await waitFor(() => expect(result.current.size).toBe(2));
    expect(result.current.get("y1")).toBe(5);
    expect(result.current.get("y2")).toBe(10);
  });

  it("falls back to an empty map when the link lookup rejects", async () => {
    vi.mocked(api.get).mockRejectedValue(new Error("network error"));

    const { result } = renderHook(() => useMyYarnInventory("g1"));

    await waitFor(() => expect(result.current).toEqual(new Map()));
  });

  it("falls back to an empty map when fetching inventory itself rejects", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.myInventory).mockRejectedValue(new Error("500"));

    const { result } = renderHook(() => useMyYarnInventory("g1"));

    await waitFor(() => expect(result.current).toEqual(new Map()));
  });

  it("re-fetches when groupId changes", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.myInventory).mockResolvedValue([entry({ yarnTypeId: "y1", quantity: 5 })]);

    const { result, rerender } = renderHook(({ groupId }) => useMyYarnInventory(groupId), {
      initialProps: { groupId: "g1" as string | null },
    });
    await waitFor(() => expect(result.current.get("y1")).toBe(5));

    vi.mocked(materialInventoryApi.myInventory).mockResolvedValue([entry({ yarnTypeId: "y3", quantity: 9 })]);
    rerender({ groupId: "g2" });

    await waitFor(() => expect(result.current.get("y3")).toBe(9));
    expect(api.get).toHaveBeenCalledWith(expect.stringContaining("g2"));
  });
});
