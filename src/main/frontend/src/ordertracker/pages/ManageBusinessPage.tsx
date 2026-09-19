import { FormEvent, useEffect, useState } from "react";
import { api, ApiError } from "../../api/client";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import { Creature } from "../../components/Creature";
import { formatDateTime } from "../../lib/format";
import type { GroupView, PendingInvite } from "../../types";

const FINANCE_APPLET_KEY = "financetracker";

/** Order Tracker's equivalent of Backlog Tracker's "Groups" page — a business here is the
 *  same underlying Group as a Backlog Tracker group (just scoped to the ordertracker
 *  applet key), so membership/invites reuse the same commons endpoints. */
export default function ManageBusinessPage() {
  const { currentBusiness, currentGroupId, refresh } = useBusiness();
  const [invite, setInvite] = useState("");
  const [renaming, setRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const [linkedFinanceGroupId, setLinkedFinanceGroupId] = useState<string | null>(null);
  const [financeGroups, setFinanceGroups] = useState<GroupView[]>([]);
  const [selectedFinanceGroupId, setSelectedFinanceGroupId] = useState("");
  const [linkLoading, setLinkLoading] = useState(true);
  const [membersWithoutProfile, setMembersWithoutProfile] = useState<string[]>([]);
  const [pendingInvites, setPendingInvites] = useState<PendingInvite[]>([]);

  const loadPendingInvites = () => {
    if (!currentGroupId) return;
    api.get<PendingInvite[]>(`/groups/${currentGroupId}/invites`).then(setPendingInvites);
  };

  useEffect(loadPendingInvites, [currentGroupId]);

  useEffect(() => {
    if (!currentGroupId || !currentBusiness) return;
    orderTrackerApi.creators(currentGroupId).then((creators) => {
      const withProfile = new Set(creators.map((c) => c.userId));
      setMembersWithoutProfile(currentBusiness.members.filter((m) => !withProfile.has(m.id)).map((m) => m.name));
    });
  }, [currentGroupId, currentBusiness]);

  useEffect(() => {
    if (!currentGroupId) return;
    setLinkLoading(true);
    Promise.all([
      api.get<{ linkedGroupId: string | null }>(`/groups/${currentGroupId}/links/${FINANCE_APPLET_KEY}`),
      api.get<GroupView[]>(`/groups?appletKey=${FINANCE_APPLET_KEY}`),
    ])
      .then(([link, groups]) => {
        setLinkedFinanceGroupId(link.linkedGroupId);
        setFinanceGroups(groups);
      })
      .finally(() => setLinkLoading(false));
  }, [currentGroupId]);

  const linkFinanceGroup = async () => {
    if (!selectedFinanceGroupId) return;
    setBusy(true);
    setErr(null);
    try {
      await api.post(`/groups/${currentGroupId}/links`, { groupId: selectedFinanceGroupId });
      setLinkedFinanceGroupId(selectedFinanceGroupId);
      setMsg("Finance group linked.");
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Could not link that finance group");
    } finally {
      setBusy(false);
    }
  };

  const unlinkFinanceGroup = async () => {
    setBusy(true);
    setErr(null);
    try {
      await api.delete(`/groups/${currentGroupId}/links/${FINANCE_APPLET_KEY}`);
      setLinkedFinanceGroupId(null);
      setSelectedFinanceGroupId("");
      setMsg("Finance group unlinked.");
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Could not unlink the finance group");
    } finally {
      setBusy(false);
    }
  };

  if (!currentBusiness || !currentGroupId) return <p className="muted">Loading…</p>;

  const linkedFinanceGroup = financeGroups.find((g) => g.id === linkedFinanceGroupId);

  const startRename = () => {
    setRenaming(true);
    setRenameValue(currentBusiness.name);
    setErr(null);
    setMsg(null);
  };

  const saveRename = async () => {
    const next = renameValue.trim();
    if (!next) return;
    setBusy(true);
    setErr(null);
    try {
      await api.patch(`/groups/${currentGroupId}`, { name: next });
      setRenaming(false);
      await refresh();
      setMsg("Business renamed.");
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Could not rename the business");
    } finally {
      setBusy(false);
    }
  };

  const sendInvite = async (e: FormEvent) => {
    e.preventDefault();
    const to = invite.trim();
    if (!to) return;
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      await api.post(`/groups/${currentGroupId}/invites`, { to });
      setInvite("");
      setMsg(`Invite sent to ${to}.`);
      loadPendingInvites();
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Could not send the invite");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1 className="page-title">Manage business</h1>
      <p className="page-sub">
        Everyone in {currentBusiness.name} has the same rights — there's no owner.
      </p>
      {err && <div className="error">{err}</div>}
      {msg && (
        <div className="hint" style={{ color: "var(--growth)", marginBottom: 12 }}>
          {msg}
        </div>
      )}

      {membersWithoutProfile.length > 0 && (
        <div className="card" style={{ marginBottom: 16, background: "var(--bg-elev-2)" }}>
          <p className="hint" style={{ margin: 0, color: "var(--urgent)" }}>
            {membersWithoutProfile.length === 1
              ? `${membersWithoutProfile[0]} hasn't set up their profile yet`
              : `${membersWithoutProfile.join(", ")} haven't set up their profiles yet`}{" "}
            — they can't be assigned any work until they do (base location + hours/day, from Business settings).
          </p>
        </div>
      )}

      <div className="card" style={{ marginBottom: 16 }}>
        <h2>{currentBusiness.name}</h2>
        {renaming ? (
          <div
            className="team-add"
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                saveRename();
              } else if (e.key === "Escape") {
                setRenaming(false);
              }
            }}
          >
            <input aria-label="Business name" autoFocus value={renameValue} maxLength={60} onChange={(e) => setRenameValue(e.target.value)} />
            <button className="primary" disabled={busy || !renameValue.trim()} onClick={saveRename}>
              Save
            </button>
            <button className="ghost" disabled={busy} onClick={() => setRenaming(false)}>
              Cancel
            </button>
          </div>
        ) : (
          <button className="linkbtn" disabled={busy} onClick={startRename}>
            Rename
          </button>
        )}

        <div className="kv" style={{ marginTop: 12 }}>
          {currentBusiness.members.map((m) => (
            <span className="chip" key={m.id}>
              <Creature seed={m.id} label={m.name} size={18} />
              {m.name} <span className="muted">@{m.handle}</span>
            </span>
          ))}
        </div>

        <form className="team-add" style={{ marginTop: 12 }} onSubmit={sendInvite}>
          <input
            aria-label="Invite by email or handle"
            placeholder="Invite by email or @handle"
            value={invite}
            onChange={(e) => setInvite(e.target.value)}
          />
          <button className="primary" disabled={busy || !invite.trim()}>
            Invite
          </button>
        </form>

        {pendingInvites.length > 0 && (
          <div style={{ marginTop: 12 }}>
            <p className="muted" style={{ fontSize: 13, marginBottom: 6 }}>
              Pending invites
            </p>
            <div className="kv">
              {pendingInvites.map((inv) => (
                <span className="chip" key={inv.id}>
                  {inv.invitedDisplay}
                  <span className="muted"> — invited by {inv.invitedByName}, {formatDateTime(inv.createdAt)}</span>
                </span>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="card">
        <h2>Finance group</h2>
        <p className="muted" style={{ marginTop: -4, marginBottom: 10 }}>
          Kept as its own separate workspace on purpose, so tracking payments doesn't have to share
          access with everyday order-taking. At most one finance group can be linked at a time.
        </p>
        {linkLoading ? (
          <p className="muted">Loading…</p>
        ) : linkedFinanceGroupId ? (
          <div className="team-add">
            <span className="chip">{linkedFinanceGroup?.name ?? "Linked finance group"}</span>
            <button className="ghost" disabled={busy} onClick={unlinkFinanceGroup}>
              Unlink
            </button>
          </div>
        ) : financeGroups.length === 0 ? (
          <p className="empty">
            No Finance Tracker groups yet — create one from the Finance Tracker applet first, then come
            back here to link it.
          </p>
        ) : (
          <div className="team-add">
            <label htmlFor="link-finance-group" className="sr-only">
              Finance group to link
            </label>
            <select
              id="link-finance-group"
              value={selectedFinanceGroupId}
              onChange={(e) => setSelectedFinanceGroupId(e.target.value)}
            >
              <option value="" disabled>
                Select a finance group…
              </option>
              {financeGroups.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name}
                </option>
              ))}
            </select>
            <button className="primary" disabled={busy || !selectedFinanceGroupId} onClick={linkFinanceGroup}>
              Link
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
