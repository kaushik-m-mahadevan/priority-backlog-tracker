import { afterEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { FinanceGroupProvider, useFinanceGroup } from "./FinanceGroupContext";
import { api } from "../api/client";
import type { GroupView } from "../types";

vi.mock("../api/client", () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

function group(overrides: Partial<GroupView> = {}): GroupView {
  return { id: "g1", name: "Founders", createdAt: "2026-01-01T00:00:00Z", members: [], ...overrides };
}

/** Thin-wrapper test for the fdup-4 refactor: FinanceGroupContext now delegates to
 *  createAppletGroupContext (already covered generically) — this just confirms the
 *  rename shim wires the generic {groups, currentGroup, ...} shape back onto Finance
 *  Tracker's own historical field names correctly, since every existing page destructures
 *  by those names. */
describe("useFinanceGroup", () => {
  afterEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("throws when used outside a FinanceGroupProvider", () => {
    expect(() => renderHook(() => useFinanceGroup())).toThrow();
  });

  it("exposes the loaded groups and selection under Finance Tracker's own field names", async () => {
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), group({ id: "g2", name: "Sunrise" })]);

    const { result } = renderHook(() => useFinanceGroup(), { wrapper: FinanceGroupProvider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(api.get).toHaveBeenCalledWith("/groups?appletKey=financetracker");
    expect(result.current.financeGroups).toHaveLength(2);
    expect(result.current.currentFinanceGroup?.id).toBe("g1");
    expect(result.current.currentGroupId).toBe("g1");
  });

  it("createFinanceGroup() creates under the financetracker appletKey and switches to it", async () => {
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" })]);
    const created = group({ id: "g2", name: "Sunrise" });
    vi.mocked(api.post).mockResolvedValue(created);

    const { result } = renderHook(() => useFinanceGroup(), { wrapper: FinanceGroupProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), created]);
    await act(async () => {
      await result.current.createFinanceGroup("Sunrise");
    });

    expect(api.post).toHaveBeenCalledWith("/groups?appletKey=financetracker", { name: "Sunrise" });
    expect(result.current.currentFinanceGroup?.id).toBe("g2");
    expect(localStorage.getItem("pbt.financetracker.groupId")).toBe("g2");
  });

  it("setCurrentFinanceGroup() updates the selection and persists it", async () => {
    vi.mocked(api.get).mockResolvedValue([group({ id: "g1" }), group({ id: "g2", name: "Sunrise" })]);
    const { result } = renderHook(() => useFinanceGroup(), { wrapper: FinanceGroupProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      result.current.setCurrentFinanceGroup("g2");
    });

    expect(result.current.currentGroupId).toBe("g2");
    expect(localStorage.getItem("pbt.financetracker.groupId")).toBe("g2");
  });
});
