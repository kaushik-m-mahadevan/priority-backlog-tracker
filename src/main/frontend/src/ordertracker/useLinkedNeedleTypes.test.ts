import { afterEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { useLinkedNeedleTypes } from "./useLinkedNeedleTypes";
import { api } from "../api/client";
import { materialInventoryApi } from "../materialinventory/api";
import type { NeedleTypeView } from "../materialinventory/types";

vi.mock("../api/client", () => ({
  api: { get: vi.fn() },
}));

vi.mock("../materialinventory/api", () => ({
  materialInventoryApi: { yarnTypes: vi.fn(), needleTypes: vi.fn() },
}));

function needleTypeView(overrides: Partial<NeedleTypeView> = {}): NeedleTypeView {
  return { id: "n1", kind: "CROCHET_HOOK", size: "4mm", notes: null, ...overrides };
}

const needleType = needleTypeView();

describe("useLinkedNeedleTypes", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("returns an empty array with no API calls when there is no group", () => {
    const { result } = renderHook(() => useLinkedNeedleTypes(null));

    expect(result.current).toEqual([]);
    expect(api.get).not.toHaveBeenCalled();
  });

  it("returns an empty array when the business has no linked material-inventory group", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: null });

    const { result } = renderHook(() => useLinkedNeedleTypes("g1"));

    await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(result.current).toEqual([]);
    expect(materialInventoryApi.needleTypes).not.toHaveBeenCalled();
  });

  it("returns the linked group's needle catalog when one is linked", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.needleTypes).mockResolvedValue([needleType]);

    const { result } = renderHook(() => useLinkedNeedleTypes("g1"));

    await waitFor(() => expect(result.current).toEqual([needleType]));
    expect(materialInventoryApi.needleTypes).toHaveBeenCalledWith("mi-1");
  });

  it("falls back to an empty array when the link lookup rejects", async () => {
    vi.mocked(api.get).mockRejectedValue(new Error("network error"));

    const { result } = renderHook(() => useLinkedNeedleTypes("g1"));

    await waitFor(() => expect(result.current).toEqual([]));
  });

  it("falls back to an empty array when fetching the needle catalog itself rejects", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.needleTypes).mockRejectedValue(new Error("500"));

    const { result } = renderHook(() => useLinkedNeedleTypes("g1"));

    await waitFor(() => expect(result.current).toEqual([]));
  });

  it("re-fetches when groupId changes", async () => {
    vi.mocked(api.get).mockResolvedValue({ linkedGroupId: "mi-1" });
    vi.mocked(materialInventoryApi.needleTypes).mockResolvedValue([needleType]);

    const { result, rerender } = renderHook(({ groupId }) => useLinkedNeedleTypes(groupId), {
      initialProps: { groupId: "g1" as string | null },
    });
    await waitFor(() => expect(result.current).toEqual([needleType]));

    const other = needleTypeView({ id: "n2", kind: "KNITTING_NEEDLE", size: "5mm" });
    vi.mocked(materialInventoryApi.needleTypes).mockResolvedValue([other]);

    rerender({ groupId: "g2" });

    await waitFor(() => expect(result.current).toEqual([other]));
    expect(api.get).toHaveBeenCalledWith(expect.stringContaining("g2"));
  });
});
