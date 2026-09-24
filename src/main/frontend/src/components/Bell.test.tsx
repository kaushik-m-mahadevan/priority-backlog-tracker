import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Bell from "./Bell";
import { api } from "../api/client";
import { useGroups } from "../groups/GroupContext";

vi.mock("../api/client", () => ({ api: { get: vi.fn(), post: vi.fn() } }));
vi.mock("../groups/GroupContext", () => ({ useGroups: vi.fn() }));

function renderBell() {
  return render(
    <MemoryRouter>
      <Bell />
    </MemoryRouter>
  );
}

function groupInvite(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: "n1", type: "GROUP_INVITE", status: "PENDING", actionable: true, title: "Group invite",
    createdAt: "2026-01-01T00:00:00Z",
    groupId: "g1", groupName: "Crochet Co", invitedByName: "Priya", archiveRequestId: null,
    itemId: null, itemTitle: null, message: null, linkPath: null,
    ...overrides,
  };
}

function archiveRequest(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: "n2", type: "ARCHIVE_REQUEST", status: "PENDING", actionable: true, title: "Archive request",
    createdAt: "2026-01-01T00:00:00Z",
    groupId: "g1", groupName: "Crochet Co", invitedByName: "Priya", archiveRequestId: "ar1",
    itemId: "i1", itemTitle: "Write onboarding doc", message: null, linkPath: null,
    ...overrides,
  };
}

describe("Bell", () => {
  const setCurrentGroup = vi.fn();
  const refreshGroups = vi.fn().mockResolvedValue(undefined);

  beforeEach(() => {
    vi.mocked(useGroups).mockReturnValue({
      groups: [], loading: false, currentGroup: null, currentGroupId: null,
      setCurrentGroup, refresh: refreshGroups,
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("shows the pending-count badge only when there's at least one pending notification", async () => {
    vi.mocked(api.get).mockResolvedValue({ items: [groupInvite()], pending: 1 });
    renderBell();
    expect(await screen.findByText("1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Notifications: 1 pending" })).toBeInTheDocument();
  });

  it("shows no badge and a zero-pending label when there's nothing pending", async () => {
    vi.mocked(api.get).mockResolvedValue({ items: [], pending: 0 });
    renderBell();
    await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(screen.getByRole("button", { name: "Notifications: 0 pending" })).toBeInTheDocument();
  });

  it("clears items/pending instead of crashing when the fetch fails", async () => {
    vi.mocked(api.get).mockRejectedValue(new Error("network down"));
    renderBell();
    await waitFor(() => expect(screen.getByRole("button", { name: "Notifications: 0 pending" })).toBeInTheDocument());
  });

  it("clicking the bell opens the popover and lists notifications; empty state shows a message", async () => {
    const user = userEvent.setup();
    vi.mocked(api.get).mockResolvedValue({ items: [], pending: 0 });
    renderBell();
    await waitFor(() => expect(api.get).toHaveBeenCalled());

    await user.click(screen.getByRole("button", { name: /notifications/i }));
    expect(screen.getByText("Nothing here yet.")).toBeInTheDocument();
  });

  it("accepting a group invite posts accept, refreshes groups, switches to the new group, and reloads", async () => {
    const user = userEvent.setup();
    vi.mocked(api.get).mockResolvedValue({ items: [groupInvite()], pending: 1 });
    vi.mocked(api.post).mockResolvedValue(undefined);
    renderBell();
    await user.click(screen.getByRole("button", { name: /notifications/i }));
    await screen.findByText("Crochet Co");

    await user.click(screen.getByRole("button", { name: "Accept" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/notifications/n1/accept", {}));
    expect(refreshGroups).toHaveBeenCalled();
    expect(setCurrentGroup).toHaveBeenCalledWith("g1");
  });

  it("declining a group invite posts decline and does NOT touch group selection", async () => {
    const user = userEvent.setup();
    vi.mocked(api.get).mockResolvedValue({ items: [groupInvite()], pending: 1 });
    vi.mocked(api.post).mockResolvedValue(undefined);
    renderBell();
    await user.click(screen.getByRole("button", { name: /notifications/i }));
    await screen.findByText("Crochet Co");

    await user.click(screen.getByRole("button", { name: "Decline" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/notifications/n1/decline", {}));
    expect(setCurrentGroup).not.toHaveBeenCalled();
  });

  it("a resolved group invite shows its outcome instead of Accept/Decline buttons", async () => {
    vi.mocked(api.get).mockResolvedValue({ items: [groupInvite({ status: "ACCEPTED" })], pending: 0 });
    const user = userEvent.setup();
    renderBell();
    await user.click(screen.getByRole("button", { name: /notifications/i }));
    await screen.findByText("Crochet Co");
    expect(screen.getByText("accepted")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Accept" })).not.toBeInTheDocument();
  });

  it("approving an archive request posts approve and notifies items-changed listeners", async () => {
    const user = userEvent.setup();
    vi.mocked(api.get).mockResolvedValue({ items: [archiveRequest()], pending: 1 });
    vi.mocked(api.post).mockResolvedValue(undefined);
    const onItemsChanged = vi.fn();
    window.addEventListener("pbt:items-changed", onItemsChanged);
    try {
      renderBell();
      await user.click(screen.getByRole("button", { name: /notifications/i }));
      await screen.findByText("Write onboarding doc");

      await user.click(screen.getByRole("button", { name: "Approve" }));

      await waitFor(() => expect(api.post).toHaveBeenCalledWith("/archive-requests/ar1/approve", {}));
      expect(onItemsChanged).toHaveBeenCalled();
    } finally {
      window.removeEventListener("pbt:items-changed", onItemsChanged);
    }
  });

  it("a resolved archive request shows the caller's own vote outcome", async () => {
    vi.mocked(api.get).mockResolvedValue({ items: [archiveRequest({ status: "DECLINED" })], pending: 0 });
    const user = userEvent.setup();
    renderBell();
    await user.click(screen.getByRole("button", { name: /notifications/i }));
    await screen.findByText("Write onboarding doc");
    expect(screen.getByText("you rejected")).toBeInTheDocument();
  });

  it("shows an info-only notification's message with no action buttons", async () => {
    vi.mocked(api.get).mockResolvedValue({
      items: [{ ...groupInvite(), type: "ARCHIVE_RESULT", status: "ACCEPTED", message: "Your archive request was approved." }],
      pending: 0,
    });
    const user = userEvent.setup();
    renderBell();
    await user.click(screen.getByRole("button", { name: /notifications/i }));
    expect(await screen.findByText("Your archive request was approved.")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeNull(); // only the bell trigger itself
    expect(screen.queryAllByRole("button")).toHaveLength(1);
  });
});
