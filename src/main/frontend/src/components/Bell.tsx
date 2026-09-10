import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { api } from "../api/client";
import { useItemsChanged } from "../lib/events";
import { useGroups } from "../groups/GroupContext";
import { BellIcon } from "./icons";
import { formatDateTime } from "../lib/format";

interface NotificationView {
  id: string;
  type: string;
  status: "PENDING" | "ACCEPTED" | "DECLINED";
  createdAt: string | null;
  groupId: string;
  groupName: string;
  invitedByName: string;
}

/** Notification inbox. Badge = pending count. Group invites are accepted/declined inline. */
export default function Bell() {
  const { refresh: refreshGroups, setCurrentGroup } = useGroups();
  const [items, setItems] = useState<NotificationView[]>([]);
  const [pending, setPending] = useState(0);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
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

  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);

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

  return (
    <div className="bell" ref={ref}>
      {pending > 0 && <span className="bub">{pending}</span>}
      <button
        className="iconbtn"
        aria-label={`Notifications: ${pending} pending`}
        onClick={() => setOpen((o) => !o)}
      >
        <BellIcon />
      </button>
      {open && (
        <div className="popover">
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>Notifications</div>
          {items.length === 0 && <p className="empty">Nothing here yet.</p>}
          {items.map((n) => (
            <div className="rp-item" key={n.id}>
              <div>
                <b>{n.invitedByName}</b> invited you to <b>{n.groupName}</b>
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
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
