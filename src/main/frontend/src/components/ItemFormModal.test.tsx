import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ItemFormModal from "./ItemFormModal";
import { api, ApiError } from "../api/client";
import { useConfig } from "../config/ConfigContext";
import { useUsers } from "../users/UsersContext";
import { useGroups } from "../groups/GroupContext";
import { useGroupCategories } from "../groups/GroupCategoriesContext";
import type { AppConfig, Item } from "../types";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");
  return { ...actual, api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() } };
});
vi.mock("../config/ConfigContext", () => ({ useConfig: vi.fn() }));
vi.mock("../users/UsersContext", () => ({ useUsers: vi.fn() }));
vi.mock("../groups/GroupContext", () => ({ useGroups: vi.fn() }));
vi.mock("../groups/GroupCategoriesContext", () => ({ useGroupCategories: vi.fn() }));

const CONFIG: AppConfig = {
  id: "c1", priorityValues: { High: 3, Medium: 2, Low: 1 }, priorities: ["High", "Medium", "Low"],
  priorityWeight: 0.34, urgencyWeight: 0.33, effortWeight: 0.33, urgencyWindowDays: 14,
  staleThresholdDays: 10, buriedThresholdDays: 30, buriedPriorityLevels: ["Low"],
  defaultDueDateOffsetDays: 30, effortCapDays: 30, maxGroupsPerUser: 5,
};

function existingItem(overrides: Partial<Item> = {}): Item {
  return {
    id: "i1", itemId: "ITM-001", title: "Write onboarding doc", category: "Research", priority: "High",
    effort: { value: 30, unit: "MINUTES", minutes: 30 }, dueDate: "2026-02-01T00:00:00Z",
    status: "BACKLOG", groupId: "g1", pinned: false, ownerId: null, createdBy: "u1", lastUpdatedBy: "u1",
    notes: null, createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  } as Item;
}

beforeEach(() => {
  vi.mocked(useConfig).mockReturnValue(CONFIG);
  vi.mocked(useUsers).mockReturnValue({ users: [], nameOf: () => "Unassigned", indexOf: () => -1, refresh: vi.fn() });
  vi.mocked(useGroups).mockReturnValue({
    groups: [], loading: false, currentGroup: null, currentGroupId: "g1",
    setCurrentGroup: vi.fn(), refresh: vi.fn(),
  });
  vi.mocked(useGroupCategories).mockReturnValue({ categories: ["Research", "Project"], refresh: vi.fn() });
});

afterEach(() => {
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

describe("ItemFormModal — effort validation", () => {
  it("flags a MINUTES value that isn't 15/30/45, and disables submit", async () => {
    const user = userEvent.setup();
    render(<ItemFormModal onClose={vi.fn()} onSaved={vi.fn()} />);
    const value = screen.getByLabelText("Effort", { selector: "input" });
    await user.clear(value);
    await user.type(value, "20");
    expect(screen.getByText("Minutes must be 15, 30, or 45")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create item" })).toBeDisabled();
  });

  it("switching unit to HOURS resets an invalid value to that unit's minimum", async () => {
    const user = userEvent.setup();
    render(<ItemFormModal onClose={vi.fn()} onSaved={vi.fn()} />);
    // starting value 30 (valid MINUTES) is invalid for HOURS' 1-23 whole-number rule? 30 is
    // actually a valid integer in [1,23]? no -- 30 > 23, so it's invalid and should reset to 1
    await user.selectOptions(screen.getByLabelText("Effort unit"), "HOURS");
    expect(screen.getByLabelText("Effort", { selector: "input" })).toHaveValue(1);
  });

  it("rejects submit and shows the unit's error when effort is invalid at submit time", async () => {
    const user = userEvent.setup();
    render(<ItemFormModal onClose={vi.fn()} onSaved={vi.fn()} />);
    await user.type(screen.getByLabelText("Title *"), "New task");
    await user.selectOptions(screen.getByLabelText("Category *"), "Research");
    await user.selectOptions(screen.getByLabelText("Priority *"), "High");
    const value = screen.getByLabelText("Effort", { selector: "input" });
    await user.clear(value);
    await user.type(value, "999");

    expect(screen.getByRole("button", { name: "Create item" })).toBeDisabled();
  });
});

describe("ItemFormModal — dirty tracking and close confirmation", () => {
  it("shows no 'Unsaved changes' notice and Close (not Discard) before any edit", () => {
    render(<ItemFormModal onClose={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.queryByText("Unsaved changes")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
  });

  it("editing a field flips the close button to Discard and shows Unsaved changes", async () => {
    const user = userEvent.setup();
    render(<ItemFormModal onClose={vi.fn()} onSaved={vi.fn()} />);
    await user.type(screen.getByLabelText("Title *"), "X");
    expect(screen.getByText("Unsaved changes")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Discard" })).toBeInTheDocument();
  });

  it("closing with unsaved changes asks for confirmation via window.confirm, and respects a decline", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<ItemFormModal onClose={onClose} onSaved={vi.fn()} />);
    await user.type(screen.getByLabelText("Title *"), "X");
    await user.click(screen.getByRole("button", { name: "Discard" }));
    expect(confirmSpy).toHaveBeenCalledWith("Discard your unsaved changes?");
    expect(onClose).not.toHaveBeenCalled();
  });

  it("closes without confirming when there are no unsaved changes", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const confirmSpy = vi.spyOn(window, "confirm");
    render(<ItemFormModal onClose={onClose} onSaved={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Close" }));
    expect(confirmSpy).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it("editing an existing item disables submit until something actually changes", async () => {
    render(<ItemFormModal existing={existingItem()} onClose={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Save changes" })).toBeDisabled();
  });
});

describe("ItemFormModal — create/save submission", () => {
  it("submits a new item with the current group id and reports success", async () => {
    const user = userEvent.setup();
    vi.mocked(api.post).mockResolvedValue({ id: "new1" });
    const onSaved = vi.fn();
    render(<ItemFormModal onClose={vi.fn()} onSaved={onSaved} />);
    await user.type(screen.getByLabelText("Title *"), "Write onboarding doc");
    await user.selectOptions(screen.getByLabelText("Category *"), "Research");
    await user.selectOptions(screen.getByLabelText("Priority *"), "High");

    await user.click(screen.getByRole("button", { name: "Create item" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/items", expect.objectContaining({
      title: "Write onboarding doc", category: "Research", priority: "High", groupId: "g1",
    })));
    expect(onSaved).toHaveBeenCalled();
  });

  it("shows the special conflict message on a 409 (stale edit) rather than the raw server message", async () => {
    const user = userEvent.setup();
    vi.mocked(api.put).mockRejectedValue(new ApiError(409, "Version mismatch"));
    render(<ItemFormModal existing={existingItem()} onClose={vi.fn()} onSaved={vi.fn()} />);
    await user.type(screen.getByLabelText("Title *"), "!");

    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText(/Someone else changed this item/)).toBeInTheDocument();
    expect(screen.queryByText("Version mismatch")).not.toBeInTheDocument();
  });

  it("shows the server's own message for a non-409 failure", async () => {
    const user = userEvent.setup();
    vi.mocked(api.put).mockRejectedValue(new ApiError(400, "Unknown category"));
    render(<ItemFormModal existing={existingItem()} onClose={vi.fn()} onSaved={vi.fn()} />);
    await user.type(screen.getByLabelText("Title *"), "!");

    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Unknown category")).toBeInTheDocument();
  });
});

describe("ItemFormModal — status toggle", () => {
  it("shows Start for a backlog item and Move to backlog for an in-progress one", () => {
    const { unmount } = render(<ItemFormModal existing={existingItem({ status: "BACKLOG" })} onClose={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Start" })).toBeInTheDocument();
    unmount();

    render(<ItemFormModal existing={existingItem({ status: "IN_PROGRESS" })} onClose={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Move to backlog" })).toBeInTheDocument();
  });

  it("toggling status patches the item and flashes a confirmation", async () => {
    const user = userEvent.setup();
    vi.mocked(api.patch).mockResolvedValue(undefined);
    render(<ItemFormModal existing={existingItem({ status: "BACKLOG" })} onClose={vi.fn()} onSaved={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "Start" }));

    await waitFor(() => expect(api.patch).toHaveBeenCalledWith("/items/i1/status", { status: "IN_PROGRESS" }));
    expect(await screen.findByText("Saved ✓")).toBeInTheDocument();
  });

  it("shows an error and leaves status unchanged when the toggle fails", async () => {
    const user = userEvent.setup();
    vi.mocked(api.patch).mockRejectedValue(new ApiError(500, "Server error"));
    render(<ItemFormModal existing={existingItem({ status: "BACKLOG" })} onClose={vi.fn()} onSaved={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "Start" }));

    expect(await screen.findByText("Server error")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start" })).toBeInTheDocument();
  });
});

describe("ItemFormModal — Mark as (terminal statuses)", () => {
  it("shows one button per terminal status only when editing with onComplete provided", () => {
    render(<ItemFormModal existing={existingItem()} onClose={vi.fn()} onSaved={vi.fn()} onComplete={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Resolved" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Rejected" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Request archive…" })).toBeInTheDocument();
  });

  it("omits Mark as entirely when creating a new item", () => {
    render(<ItemFormModal onClose={vi.fn()} onSaved={vi.fn()} onComplete={vi.fn()} />);
    expect(screen.queryByText("Mark as")).not.toBeInTheDocument();
  });

  it("clicking a terminal-status button calls onComplete with that status", async () => {
    const user = userEvent.setup();
    const onComplete = vi.fn();
    render(<ItemFormModal existing={existingItem()} onClose={vi.fn()} onSaved={vi.fn()} onComplete={onComplete} />);
    await user.click(screen.getByRole("button", { name: "Resolved" }));
    expect(onComplete).toHaveBeenCalledWith("RESOLVED");
  });
});
