import { afterEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { BusinessProvider, useBusiness } from "./BusinessContext";
import { api } from "../api/client";
import type { GroupView } from "../types";

vi.mock("../api/client", () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

function group(overrides: Partial<GroupView> = {}): GroupView {
  return { id: "g1", name: "My Shop", createdAt: "2026-01-01T00:00:00Z", members: [], ...overrides };
}

describe("useBusiness", () => {
  afterEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("throws when used outside a BusinessProvider", () => {
    expect(() => renderHook(() => useBusiness())).toThrow("useBusiness must be used within BusinessProvider");
  });

  it("loads the ordertracker-scoped businesses and defaults to the first one", async () => {
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), group({ id: "g2" })]);

    const { result } = renderHook(() => useBusiness(), { wrapper: BusinessProvider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(api.get).toHaveBeenCalledWith(expect.stringContaining("appletKey=ordertracker"));
    expect(result.current.currentGroupId).toBe("g1");
    expect(result.current.businesses).toHaveLength(2);
  });

  it("keeps a previously-stored selection if it is still in the list", async () => {
    localStorage.setItem("pbt.ordertracker.groupId", "g2");
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), group({ id: "g2" })]);

    const { result } = renderHook(() => useBusiness(), { wrapper: BusinessProvider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.currentGroupId).toBe("g2");
  });

  it("clears the list and stops loading when the fetch rejects", async () => {
    vi.mocked(api.get).mockRejectedValue(new Error("network error"));

    const { result } = renderHook(() => useBusiness(), { wrapper: BusinessProvider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.businesses).toEqual([]);
    expect(result.current.currentGroupId).toBeNull();
  });

  it("setCurrentBusiness() updates selection and persists it to localStorage", async () => {
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), group({ id: "g2" })]);
    const { result } = renderHook(() => useBusiness(), { wrapper: BusinessProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      result.current.setCurrentBusiness("g2");
    });

    expect(result.current.currentGroupId).toBe("g2");
    expect(localStorage.getItem("pbt.ordertracker.groupId")).toBe("g2");
  });

  it("createBusiness() creates the group, starts its setup wizard, refreshes, and selects it", async () => {
    vi.mocked(api.get).mockResolvedValue([]);
    const { result } = renderHook(() => useBusiness(), { wrapper: BusinessProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    const created = group({ id: "g-new", name: "New Shop" });
    vi.mocked(api.post).mockResolvedValueOnce(created); // POST /groups
    vi.mocked(api.post).mockResolvedValueOnce(undefined); // POST setup/start
    vi.mocked(api.get).mockResolvedValue([created]); // refresh()

    let returned: GroupView | undefined;
    await act(async () => {
      returned = await result.current.createBusiness("New Shop");
    });

    expect(returned).toEqual(created);
    expect(api.post).toHaveBeenCalledWith(expect.stringContaining("appletKey=ordertracker"), { name: "New Shop" });
    expect(api.post).toHaveBeenCalledWith(
      expect.stringContaining("/ordertracker/groups/g-new/business-config/setup/start")
    );
    expect(result.current.currentGroupId).toBe("g-new");
  });
});
