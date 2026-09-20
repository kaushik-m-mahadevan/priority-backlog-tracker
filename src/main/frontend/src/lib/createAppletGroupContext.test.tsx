import { afterEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { createAppletGroupContext } from "./createAppletGroupContext";
import { api } from "../api/client";
import type { GroupView } from "../types";

vi.mock("../api/client", () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

function group(overrides: Partial<GroupView> = {}): GroupView {
  return { id: "g1", name: "Group One", createdAt: "2026-01-01T00:00:00Z", members: [], ...overrides };
}

describe("createAppletGroupContext", () => {
  afterEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("throws when the hook is used outside its own Provider", () => {
    const { useAppletGroup } = createAppletGroupContext("widgets", "widgets");
    expect(() => renderHook(() => useAppletGroup())).toThrow('useAppletGroup("widgets") must be used within its Provider');
  });

  it("scopes the groups fetch to the given appletKey and defaults to the first group", async () => {
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), group({ id: "g2" })]);
    const { Provider, useAppletGroup } = createAppletGroupContext("widgets", "widgets");

    const { result } = renderHook(() => useAppletGroup(), { wrapper: Provider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(api.get).toHaveBeenCalledWith("/groups?appletKey=widgets");
    expect(result.current.currentGroupId).toBe("g1");
  });

  it("keeps a previously-stored selection under its own storage namespace", async () => {
    localStorage.setItem("pbt.widgets.groupId", "g2");
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), group({ id: "g2" })]);
    const { Provider, useAppletGroup } = createAppletGroupContext("widgets", "widgets");

    const { result } = renderHook(() => useAppletGroup(), { wrapper: Provider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.currentGroupId).toBe("g2");
  });

  it("two factory instances never see each other's stored selection or groups", async () => {
    localStorage.setItem("pbt.alpha.groupId", "a1");
    localStorage.setItem("pbt.beta.groupId", "b1");
    vi.mocked(api.get).mockImplementation((url: string) =>
      Promise.resolve(url.includes("alpha") ? [group({ id: "a1" }), group({ id: "a2" })] : [group({ id: "b1" }), group({ id: "b2" })])
    );
    const alpha = createAppletGroupContext("alpha", "alpha");
    const beta = createAppletGroupContext("beta", "beta");

    const alphaHook = renderHook(() => alpha.useAppletGroup(), { wrapper: alpha.Provider });
    const betaHook = renderHook(() => beta.useAppletGroup(), { wrapper: beta.Provider });

    await waitFor(() => expect(alphaHook.result.current.loading).toBe(false));
    await waitFor(() => expect(betaHook.result.current.loading).toBe(false));

    expect(alphaHook.result.current.currentGroupId).toBe("a1");
    expect(betaHook.result.current.currentGroupId).toBe("b1");
    expect(api.get).toHaveBeenCalledWith("/groups?appletKey=alpha");
    expect(api.get).toHaveBeenCalledWith("/groups?appletKey=beta");
  });

  it("createGroup() posts to this appletKey, refreshes, and switches to the new group", async () => {
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" })]);
    const created = group({ id: "g2", name: "New Group" });
    vi.mocked(api.post).mockResolvedValue(created);
    const { Provider, useAppletGroup } = createAppletGroupContext("widgets", "widgets");

    const { result } = renderHook(() => useAppletGroup(), { wrapper: Provider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), created]);
    await act(async () => {
      await result.current.createGroup("New Group");
    });

    expect(api.post).toHaveBeenCalledWith("/groups?appletKey=widgets", { name: "New Group" });
    expect(result.current.currentGroupId).toBe("g2");
    expect(localStorage.getItem("pbt.widgets.groupId")).toBe("g2");
  });

  it("clears the list and stops loading when the fetch rejects", async () => {
    vi.mocked(api.get).mockRejectedValue(new Error("network error"));
    const { Provider, useAppletGroup } = createAppletGroupContext("widgets", "widgets");

    const { result } = renderHook(() => useAppletGroup(), { wrapper: Provider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.groups).toEqual([]);
    expect(result.current.currentGroupId).toBeNull();
  });
});
