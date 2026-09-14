import { FormEvent, useState } from "react";
import { api, ApiError } from "../../api/client";
import { useBusiness } from "../BusinessContext";
import { Creature } from "../../components/Creature";

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

  if (!currentBusiness || !currentGroupId) return <p className="muted">Loading…</p>;

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
            <input autoFocus value={renameValue} maxLength={60} onChange={(e) => setRenameValue(e.target.value)} />
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
            placeholder="Invite by email or @handle"
            value={invite}
            onChange={(e) => setInvite(e.target.value)}
          />
          <button className="primary" disabled={busy || !invite.trim()}>
            Invite
          </button>
        </form>
      </div>
    </div>
  );
}
