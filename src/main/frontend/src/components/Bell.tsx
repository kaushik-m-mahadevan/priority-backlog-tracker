import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { api } from "../api/client";
import { APPLETS } from "../api/applets";
import { useItemsChanged, notifyItemsChanged } from "../lib/events";
import { useDismissableMenu } from "../lib/useDismissableMenu";
import { usePopoverPosition } from "../lib/usePopoverPosition";
import { useGroups } from "../groups/GroupContext";
import { BellIcon } from "./icons";
import { formatDateTime } from "../lib/format";

/** mb-2: two groups from different applets can share a name (e.g. a Backlog Tracker
 *  group and a business both called "Founders") and otherwise look identical in the
 *  inbox — a small applet icon disambiguates which one a notification is actually about. */
function appletIcon(appletKey: string | null): string | null {
  return APPLETS.find((a) => a.key === appletKey)?.icon ?? null;
}

/** Every purely-informational, message-only notification type — rendered identically,
 *  regardless of which applet's workflow raised it (see the render logic below). */
const INFO_ONLY_TYPES = [
  "ARCHIVE_RESULT",
  "PASSWORD_RESULT",
  "COST_CONFIG_INVALIDATED",
  "ORDER_FINALIZATION_INVALIDATED",
  "PROFIT_DISTRIBUTION_INVALIDATED",
  "ARCHIVE_REQUEST_INVALIDATED",
  // ad-4: proactive "something new needs your attention" notices
  "COST_CONFIG_PROPOSED",
  "ORDER_FINALIZATION_PROPOSED",
  "ORDER_FINALIZATION_RESOLVED",
  "PROFIT_DISTRIBUTION_PROPOSED",
  "SIGNUP_PENDING",
  "PASSWORD_REQUEST_PENDING",
] as const;

interface NotificationView {
  id: string;
  type: "GROUP_INVITE" | "ARCHIVE_REQUEST" | (typeof INFO_ONLY_TYPES)[number];
  status: "PENDING" | "ACCEPTED" | "DECLINED";
  createdAt: string | null;
  groupId: string;
  groupName: string;
  groupAppletKey: string | null;
  invitedByName: string;
  archiveRequestId: string | null;
  itemId: string | null;
  itemTitle: string | null;
  message: string | null;
}

/** Notification inbox. Badge = pending count. Group invites are accepted/declined inline. */
export default function Bell() {
  const { refresh: refreshGroups, setCurrentGroup } = useGroups();
  const [items, setItems] = useState<NotificationView[]>([]);
  const [pending, setPending] = useState(0);
  const { open, setOpen, ref } = useDismissableMenu<HTMLDivElement>();
  const triggerRef = useRef<HTMLButtonElement>(null);
  const { popoverRef, style: popoverStyle } = usePopoverPosition(triggerRef, open);
  const [busy, setBusy] = useState(false);
  const loc = useLocation();

  const load = useCallback(() => {
    api
      .get<{ items: NotificationView[]; pending: number }>("/notifications")
      .then((r) => {
        setItems(r.items);
        setPending(r.pending);
      })
      .catch(() => {
        setItems([]);
        setPending(0);
      });
  }, []);

  useEffect(load, [load, loc.pathname]);
  useItemsChanged(load);

  async function act(n: NotificationView, what: "accept" | "decline") {
    setBusy(true);
    try {
      await api.post(`/notifications/${n.id}/${what}`, {});
      if (what === "accept") {
        await refreshGroups();
        setCurrentGroup(n.groupId);
      }
      load();
    } catch {
      load();
    } finally {
      setBusy(false);
    }
  }

  async function voteArchive(n: NotificationView, decision: "approve" | "reject") {
    if (!n.archiveRequestId) return;
    setBusy(true);
    try {
      await api.post(`/archive-requests/${n.archiveRequestId}/${decision}`, {});
      notifyItemsChanged(); // the item may have just been archived
      load();
    } catch {
      load();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="bell" ref={ref}>
      {pending > 0 && <span className="bub">{pending}</span>}
      <button
        ref={triggerRef}
        className="iconbtn"
        aria-label={`Notifications: ${pending} pending`}
        onClick={() => setOpen((o) => !o)}
      >
        <BellIcon />
      </button>
      {open && (
        <div className="popover" ref={popoverRef} style={popoverStyle}>
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>Notifications</div>
          {items.length === 0 && <p className="empty">Nothing here yet.</p>}
          {items.map((n) => (
            <div className="rp-item" key={n.id}>
              {n.type === "GROUP_INVITE" && (
                <>
                  <div>
                    <b>{n.invitedByName}</b> invited you to{" "}
                    {appletIcon(n.groupAppletKey) && <span title="applet">{appletIcon(n.groupAppletKey)} </span>}
                    <b>{n.groupName}</b>
                  </div>
                  <div className="sub">{formatDateTime(n.createdAt)}</div>
                  {n.status === "PENDING" ? (
                    <div className="row-actions" style={{ marginTop: 6 }}>
                      <button className="primary" disabled={busy} onClick={() => act(n, "accept")}>
                        Accept
                      </button>
                      <button className="ghost" disabled={busy} onClick={() => act(n, "decline")}>
                        Decline
                      </button>
                    </div>
                  ) : (
                    <div className="sub muted">{n.status.toLowerCase()}</div>
                  )}
                </>
              )}

              {n.type === "ARCHIVE_REQUEST" && (
                <>
                  <div>
                    <b>{n.invitedByName}</b> wants to archive <b>{n.itemTitle}</b>
                  </div>
                  {n.message && <div className="sub">“{n.message}”</div>}
                  <div className="sub">{formatDateTime(n.createdAt)}</div>
                  {n.status === "PENDING" ? (
                    <div className="row-actions" style={{ marginTop: 6 }}>
                      <button
                        className="primary"
                        disabled={busy}
                        onClick={() => voteArchive(n, "approve")}
                      >
                        Approve
                      </button>
                      <button
                        className="ghost"
                        disabled={busy}
                        onClick={() => voteArchive(n, "reject")}
                      >
                        Reject
                      </button>
                    </div>
                  ) : (
                    <div className="sub muted">
                      {n.status === "ACCEPTED" ? "you approved" : "you rejected"}
                    </div>
                  )}
                </>
              )}

              {(INFO_ONLY_TYPES as readonly string[]).includes(n.type) && (
                <>
                  <div>{n.message}</div>
                  <div className="sub">{formatDateTime(n.createdAt)}</div>
                </>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
