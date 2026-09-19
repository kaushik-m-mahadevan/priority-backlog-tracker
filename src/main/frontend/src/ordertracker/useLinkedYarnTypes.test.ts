import { afterEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { useLinkedYarnTypes } from "./useLinkedYarnTypes";
import { api } from "../api/client";
import { materialInventoryApi } from "../materialinventory/api";
import type { YarnTypeView } from "../materialinventory/types";

vi.mock("../api/client", () => ({
  api: { get: vi.fn() },
}));

vi.mock("../materialinventory/api", () => ({
  materialInventoryApi: { yarnTypes: vi.fn(), needleTypes: vi.fn() },
}));

function yarnTypeView(overrides: Partial<YarnTypeView> = {}): YarnTypeView {
  return {
    id: "y1", brand: "Red Heart", thickness: "Worsted", colour: "Cream", material: null,
    skeinWeightGrams: null, skeinLengthMeters: null, recommendedHookSize: null, notes: null,
    costPerSkein: null, costHistory: [],
    ...overrides,
  };
}

const yarnType = yarnTypeView();

describe("useLinkedYarnTypes", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("returns an empty array with no API calls when there is no group", () => {
    const { result } = renderHook(() => useLinkedYarnTypes(null));

    expect(result.current).toEqual([]);
    expect(api.get).not.toHaveBeenCalled();
  });

  it("returns an empty array when the business has no linked material-inventory group", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: null });

    const { result } = renderHook(() => useLinkedYarnTypes("g1"));

    await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(result.current).toEqual([]);
    expect(materialInventoryApi.yarnTypes).not.toHaveBeenCalled();
  });

  it("returns the linked group's yarn catalog when one is linked", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.yarnTypes).mockResolvedValue([yarnType]);

    const { result } = renderHook(() => useLinkedYarnTypes("g1"));

    await waitFor(() => expect(result.current).toEqual([yarnType]));
    expect(materialInventoryApi.yarnTypes).toHaveBeenCalledWith("mi-1");
  });

  it("falls back to an empty array when the link lookup rejects", async () => {
    vi.mocked(api.get).mockRejectedValue(new Error("network error"));

    const { result } = renderHook(() => useLinkedYarnTypes("g1"));

    await waitFor(() => expect(result.current).toEqual([]));
  });

  it("falls back to an empty array when fetching the yarn catalog itself rejects", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.yarnTypes).mockRejectedValue(new Error("500"));

    const { result } = renderHook(() => useLinkedYarnTypes("g1"));

    await waitFor(() => expect(result.current).toEqual([]));
  });

  it("re-fetches when groupId changes", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.yarnTypes).mockResolvedValue([yarnType]);

    const { result, rerender } = renderHook(({ groupId }) => useLinkedYarnTypes(groupId), {
      initialProps: { groupId: "g1" as string | null },
    });
    await waitFor(() => expect(result.current).toEqual([yarnType]));

    const other = yarnTypeView({ id: "y2", brand: "Lion Brand", thickness: "Bulky", colour: "Grey" });
    vi.mocked(materialInventoryApi.yarnTypes).mockResolvedValue([other]);

    rerender({ groupId: "g2" });

    await waitFor(() => expect(result.current).toEqual([other]));
    expect(api.get).toHaveBeenCalledWith(expect.stringContaining("g2"));
  });
});
