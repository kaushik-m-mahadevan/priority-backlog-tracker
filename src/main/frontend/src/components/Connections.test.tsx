import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Connections from "./Connections";
import { ApiError } from "../api/client";
import { groupLinkApi } from "../api/groupLinks";

vi.mock("../api/groupLinks", () => ({
  groupLinkApi: { linkedGroupId: vi.fn(), myGroupsIn: vi.fn(), group: vi.fn(), link: vi.fn(), unlink: vi.fn(), invite: vi.fn() },
}));

function renderConnections(groupId: string | null = "g1") {
  return render(
    <MemoryRouter>
      <Connections appletKey="backlogtracker" groupId={groupId} />
    </MemoryRouter>
  );
}

const OTHER_APPLET_COUNT = 4; // every applet except backlogtracker

describe("Connections", () => {
  beforeEach(() => {
    vi.mocked(groupLinkApi.linkedGroupId).mockResolvedValue(null);
    vi.mocked(groupLinkApi.myGroupsIn).mockResolvedValue([]);
    vi.mocked(groupLinkApi.group).mockResolvedValue({ id: "g1", name: "Current group", createdAt: null, members: [] });
    vi.mocked(groupLinkApi.invite).mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("renders nothing at all when there's no current group", () => {
    const { container } = renderConnections(null);
    expect(container).toBeEmptyDOMElement();
  });

  it("lists every other applet once opened, and settles out of the loading state once link status resolves", async () => {
    const user = userEvent.setup();
    renderConnections();
    await user.click(screen.getByRole("button", { name: "Connections to other applets" }));
    await waitFor(() => expect(screen.queryAllByText("Loading…")).toHaveLength(0));
    expect(screen.getAllByRole("button", { name: /No .+ groups yet/ })).toHaveLength(OTHER_APPLET_COUNT);
  });

  it("shows 'No <applet> groups yet', disabled, when the user belongs to none of that applet's groups", async () => {
    const user = userEvent.setup();
    renderConnections();
    await user.click(screen.getByRole("button", { name: "Connections to other applets" }));
    const button = await screen.findByRole("button", { name: "No Order Tracker groups yet" });
    expect(button).toBeDisabled();
  });

  it("shows a Link… affordance when the user has candidate groups in that applet", async () => {
    vi.mocked(groupLinkApi.myGroupsIn).mockImplementation(async (key: string) =>
      key === "ordertracker" ? [{ id: "ot1", name: "Crochet Co", appletKey: "ordertracker", members: [] } as never] : []
    );
    const user = userEvent.setup();
    renderConnections();
    await user.click(screen.getByRole("button", { name: "Connections to other applets" }));
    expect(await screen.findByRole("button", { name: "Link…" })).toBeEnabled();
  });

  it("shows the linked group's name and an Unlink action when already linked", async () => {
    vi.mocked(groupLinkApi.linkedGroupId).mockImplementation(async (_g, key) => (key === "ordertracker" ? "ot1" : null));
    vi.mocked(groupLinkApi.myGroupsIn).mockImplementation(async (key: string) =>
      key === "ordertracker" ? [{ id: "ot1", name: "Crochet Co", appletKey: "ordertracker", members: [] } as never] : []
    );
    const user = userEvent.setup();
    renderConnections();
    await user.click(screen.getByRole("button", { name: "Connections to other applets" }));
    expect(await screen.findByText("Crochet Co")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Unlink" })).toBeInTheDocument();
  });

  it("selecting a group, reviewing, and confirming calls groupLinkApi.link and switches the row to linked", async () => {
    vi.mocked(groupLinkApi.myGroupsIn).mockImplementation(async (key: string) =>
      key === "ordertracker" ? [{ id: "ot1", name: "Crochet Co", appletKey: "ordertracker", members: [] } as never] : []
    );
    vi.mocked(groupLinkApi.link).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderConnections();
    await user.click(screen.getByRole("button", { name: "Connections to other applets" }));
    await user.click(await screen.findByRole("button", { name: "Link…" }));
    await user.selectOptions(screen.getByLabelText("Group to link in Order Tracker"), "ot1");
    await user.click(screen.getByRole("button", { name: "Review & link" }));
    await user.click(await screen.findByRole("button", { name: "Confirm link" }));

    await waitFor(() => expect(groupLinkApi.link).toHaveBeenCalledWith("g1", "ot1"));
    expect(await screen.findByText("Crochet Co")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Unlink" })).toBeInTheDocument();
  });

  it("shows the ApiError's own message when linking fails", async () => {
    vi.mocked(groupLinkApi.myGroupsIn).mockImplementation(async (key: string) =>
      key === "ordertracker" ? [{ id: "ot1", name: "Crochet Co", appletKey: "ordertracker", members: [] } as never] : []
    );
    vi.mocked(groupLinkApi.link).mockRejectedValue(new ApiError(409, "That group is already linked to a business"));
    const user = userEvent.setup();
    renderConnections();
    await user.click(screen.getByRole("button", { name: "Connections to other applets" }));
    await user.click(await screen.findByRole("button", { name: "Link…" }));
    await user.selectOptions(screen.getByLabelText("Group to link in Order Tracker"), "ot1");
    await user.click(screen.getByRole("button", { name: "Review & link" }));
    await user.click(await screen.findByRole("button", { name: "Confirm link" }));

    expect(await screen.findByText("That group is already linked to a business")).toBeInTheDocument();
  });

  it("falls back to a generic message when linking fails with a non-ApiError", async () => {
    vi.mocked(groupLinkApi.myGroupsIn).mockImplementation(async (key: string) =>
      key === "ordertracker" ? [{ id: "ot1", name: "Crochet Co", appletKey: "ordertracker", members: [] } as never] : []
    );
    vi.mocked(groupLinkApi.link).mockRejectedValue(new Error("boom"));
    const user = userEvent.setup();
    renderConnections();
    await user.click(screen.getByRole("button", { name: "Connections to other applets" }));
    await user.click(await screen.findByRole("button", { name: "Link…" }));
    await user.selectOptions(screen.getByLabelText("Group to link in Order Tracker"), "ot1");
    await user.click(screen.getByRole("button", { name: "Review & link" }));
    await user.click(await screen.findByRole("button", { name: "Confirm link" }));

    expect(await screen.findByText("Could not link")).toBeInTheDocument();
  });

  it("clicking Unlink calls groupLinkApi.unlink and reverts the row to unlinked", async () => {
    vi.mocked(groupLinkApi.linkedGroupId).mockImplementation(async (_g, key) => (key === "ordertracker" ? "ot1" : null));
    vi.mocked(groupLinkApi.myGroupsIn).mockImplementation(async (key: string) =>
      key === "ordertracker" ? [{ id: "ot1", name: "Crochet Co", appletKey: "ordertracker", members: [] } as never] : []
    );
    vi.mocked(groupLinkApi.unlink).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderConnections();
    await user.click(screen.getByRole("button", { name: "Connections to other applets" }));
    await screen.findByText("Crochet Co");

    await user.click(screen.getByRole("button", { name: "Unlink" }));

    await waitFor(() => expect(groupLinkApi.unlink).toHaveBeenCalledWith("g1", "ordertracker"));
    expect(await screen.findByRole("button", { name: "Link…" })).toBeInTheDocument();
  });

  /** mb-23: the review step suggests every two-way missing member, checked by default, and
   *  unchecking one keeps it out of the invites actually sent on confirm. */
  it("shows a suggested invite for a target-group member missing from the current group, and skips it when unchecked", async () => {
    vi.mocked(groupLinkApi.myGroupsIn).mockImplementation(async (key: string) =>
      key === "ordertracker"
        ? [{
            id: "ot1", name: "Crochet Co", appletKey: "ordertracker",
            members: [{ id: "u-missing", name: "Missing Person", handle: "mp", email: "mp@x.test", role: "USER", status: "ACTIVE" }],
          } as never]
        : []
    );
    vi.mocked(groupLinkApi.link).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderConnections();
    await user.click(screen.getByRole("button", { name: "Connections to other applets" }));
    await user.click(await screen.findByRole("button", { name: "Link…" }));
    await user.selectOptions(screen.getByLabelText("Group to link in Order Tracker"), "ot1");
    await user.click(screen.getByRole("button", { name: "Review & link" }));

    const checkbox = await screen.findByRole("checkbox", { name: "Missing Person" });
    expect(checkbox).toBeChecked();
    await user.click(checkbox);
    await user.click(screen.getByRole("button", { name: "Confirm link" }));

    await waitFor(() => expect(groupLinkApi.link).toHaveBeenCalledWith("g1", "ot1"));
    expect(groupLinkApi.invite).not.toHaveBeenCalled();
  });

  it("the Review & link button stays disabled until a group is actually selected", async () => {
    vi.mocked(groupLinkApi.myGroupsIn).mockImplementation(async (key: string) =>
      key === "ordertracker" ? [{ id: "ot1", name: "Crochet Co", appletKey: "ordertracker", members: [] } as never] : []
    );
    const user = userEvent.setup();
    renderConnections();
    await user.click(screen.getByRole("button", { name: "Connections to other applets" }));
    await user.click(await screen.findByRole("button", { name: "Link…" }));
    expect(screen.getByRole("button", { name: "Review & link" })).toBeDisabled();
  });
});
