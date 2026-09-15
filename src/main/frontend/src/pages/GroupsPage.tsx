import { FormEvent, useState } from "react";
import { api, ApiError } from "../api/client";
import { useGroups } from "../groups/GroupContext";
import { useAuth } from "../auth/AuthContext";
import { Creature } from "../components/Creature";

export default function GroupsPage() {
  const { groups, currentGroupId, setCurrentGroup, refresh } = useGroups();
  const { user } = useAuth();
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [invite, setInvite] = useState<Record<string, string>>({});
  const [renaming, setRenaming] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState("");

  function startRename(id: string, current: string) {
    setRenaming(id);
    setRenameValue(current);
    setErr(null);
    setMsg(null);
  }

  async function saveRename(id: string) {
    const next = renameValue.trim();
    if (!next) return;
    setBusy(true);
    setErr(null);
    try {
      await api.patch(`/groups/${id}`, { name: next });
      setRenaming(null);
      await refresh();
      setMsg("Group renamed.");
    } catch (e2) {
      setErr(e2 instanceof ApiError ? e2.message : "Could not rename the group");
    } finally {
      setBusy(false);
    }
  }

  async function sendInvite(groupId: string) {
    const to = (invite[groupId] ?? "").trim();
    if (!to) return;
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      await api.post(`/groups/${groupId}/invites`, { to });
      setInvite((m) => ({ ...m, [groupId]: "" }));
      setMsg(`Invite sent to ${to}.`);
    } catch (e2) {
      setErr(e2 instanceof ApiError ? e2.message : "Could not send the invite");
    } finally {
      setBusy(false);
    }
  }

  async function create(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setBusy(true);
    setErr(null);
    try {
      const g = await api.post<{ id: string }>("/groups", { name: name.trim() });
      setName("");
      await refresh();
      setCurrentGroup(g.id);
    } catch (e2) {
      setErr(e2 instanceof ApiError ? e2.message : "Could not create the group");
    } finally {
      setBusy(false);
    }
  }

  async function leave(id: string, groupName: string, sole: boolean) {
    const msg = sole
      ? `You're the only member of "${groupName}". Leaving permanently deletes the group and all of its items. Continue?`
      : `Leave "${groupName}"? You can only be added back by a member.`;
    if (!window.confirm(msg)) return;
    setBusy(true);
    setErr(null);
    try {
      await api.delete(`/groups/${id}/members/me`);
      await refresh();
    } catch (e2) {
      setErr(e2 instanceof ApiError ? e2.message : "Could not leave the group");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h1 className="page-title">Groups</h1>
      <p className="page-sub">
        Shared workspaces. Everyone in a group has the same rights — there's no owner.
      </p>
      {err && <div className="error">{err}</div>}
      {msg && <div className="hint" style={{ color: "var(--growth)", marginBottom: 12 }}>{msg}</div>}

      <div className="card" style={{ marginBottom: 16 }}>
        <h2>New group</h2>
        <form className="team-add" onSubmit={create}>
          <input
            aria-label="Group name"
            placeholder="Group name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={60}
          />
          <button className="primary" disabled={busy || !name.trim()}>
            Create
          </button>
        </form>
      </div>

      {groups.length === 0 ? (
        <div className="card">
          <p className="empty">
            You're not in any group yet. Create one above, or ask a teammate to add you.
          </p>
        </div>
      ) : (
        groups.map((g) => {
          const sole = g.members.length === 1 && g.members[0].id === user?.id;
          return (
            <div className="card" key={g.id} style={{ marginBottom: 12 }}>
              <div className="team-row" style={{ border: "none", padding: 0 }}>
                <div style={{ flex: 1 }}>
                  {renaming === g.id ? (
                    <div
                      className="team-add"
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          e.preventDefault();
                          saveRename(g.id);
                        } else if (e.key === "Escape") {
                          setRenaming(null);
                        }
                      }}
                    >
                      <input
                        aria-label="Group name"
                        autoFocus
                        value={renameValue}
                        maxLength={60}
                        onChange={(e) => setRenameValue(e.target.value)}
                      />
                      <button
                        className="primary"
                        disabled={busy || !renameValue.trim()}
                        onClick={() => saveRename(g.id)}
                      >
                        Save
                      </button>
                      <button className="ghost" disabled={busy} onClick={() => setRenaming(null)}>
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <>
                      <div className="team-name">
                        {g.name}
                        {g.id === currentGroupId && (
                          <span className="muted" style={{ fontSize: 12 }}> · current</span>
                        )}
                        <button
                          className="linkbtn"
                          style={{ marginLeft: 8, fontSize: 12 }}
                          disabled={busy}
                          onClick={() => startRename(g.id, g.name)}
                        >
                          Rename
                        </button>
                      </div>
                      <div className="muted" style={{ fontSize: 12 }}>
                        {g.members.length} member{g.members.length === 1 ? "" : "s"}
                      </div>
                    </>
                  )}
                </div>
                <button
                  disabled={busy || g.id === currentGroupId}
                  onClick={() => setCurrentGroup(g.id)}
                >
                  {g.id === currentGroupId ? "Selected" : "Switch to"}
                </button>
                <button className="ghost" disabled={busy} onClick={() => leave(g.id, g.name, sole)}>
                  Leave
                </button>
              </div>
              <div className="kv" style={{ marginTop: 10 }}>
                {g.members.map((m) => (
                  <span className="chip" key={m.id}>
                    <Creature seed={m.id} label={m.name} size={18} />
                    {m.name} <span className="muted">@{m.handle}</span>
                  </span>
                ))}
              </div>
              <div
                className="team-add"
                style={{ marginTop: 10 }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    sendInvite(g.id);
                  }
                }}
              >
                <input
                  aria-label="Invite by email or handle"
                  placeholder="Invite by email or @handle"
                  value={invite[g.id] ?? ""}
                  onChange={(e) => setInvite((m) => ({ ...m, [g.id]: e.target.value }))}
                />
                <button
                  disabled={busy || !(invite[g.id] ?? "").trim()}
                  onClick={() => sendInvite(g.id)}
                >
                  Invite
                </button>
              </div>
            </div>
          );
        })
      )}
    </div>
  );
}
