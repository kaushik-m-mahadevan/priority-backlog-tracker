import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "../api/client";
import { capitalize } from "../lib/format";

interface Row {
  id: string;
  name: string;
  handle: string;
  email: string;
  role: string;
  status: string;
}

interface PwRow {
  id: string;
  userName: string;
  userEmail: string;
  type: "CHANGE" | "RESET";
  status: string;
  createdAt: string | null;
}

export default function AdminPage() {
  const [pending, setPending] = useState<Row[]>([]);
  const [roster, setRoster] = useState<Row[]>([]);
  const [pwReqs, setPwReqs] = useState<PwRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(() => {
    Promise.all([
      api.get<Row[]>("/admin/pending-users"),
      api.get<Row[]>("/admin/users"),
      api.get<PwRow[]>("/admin/password-requests"),
    ])
      .then(([p, r, pw]) => {
        setPending(p);
        setRoster(r);
        setPwReqs(pw);
      })
      .catch((e) => setErr(e instanceof ApiError ? e.message : "Could not load"));
  }, []);

  useEffect(load, [load]);

  async function act(id: string, what: "approve" | "reject", name: string) {
    if (what === "reject" && !window.confirm(`Reject ${name}'s registration? This deletes the request.`)) {
      return;
    }
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      await api.post(`/admin/users/${id}/${what}`, {});
      setMsg(what === "approve" ? `${name} approved.` : `${name} rejected.`);
      load();
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Action failed");
    } finally {
      setBusy(false);
    }
  }

  async function pwAct(r: PwRow, what: "approve" | "reject") {
    let body: unknown = {};
    if (what === "approve" && r.type === "RESET") {
      const tmp = window.prompt(
        `Set a temporary password for ${r.userName} (min 8 chars).\nGive it to them directly — it isn't emailed.`,
        "",
      );
      if (tmp === null) return;
      if (tmp.length < 8) {
        setErr("Temporary password must be at least 8 characters.");
        return;
      }
      body = { temporaryPassword: tmp };
    } else if (what === "reject") {
      if (!window.confirm(`Reject ${r.userName}'s password request?`)) return;
    }
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      await api.post(`/admin/password-requests/${r.id}/${what}`, body);
      setMsg(`Password request ${what === "approve" ? "approved" : "rejected"}.`);
      load();
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Action failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h1 className="page-title">Admin</h1>
      <p className="page-sub">Approve new accounts. {pending.length} waiting.</p>
      {err && <div className="error">{err}</div>}
      {msg && <div className="hint" style={{ color: "var(--growth)", marginBottom: 12 }}>{msg}</div>}

      <div className="card" style={{ marginBottom: 16 }}>
        <h2>Awaiting approval</h2>
        {pending.length === 0 ? (
          <p className="empty">Nobody waiting.</p>
        ) : (
          <div className="team-list">
            {pending.map((u) => (
              <div className="team-row" key={u.id}>
                <div>
                  <div className="team-name">
                    {u.name} <span className="muted">@{u.handle}</span>
                  </div>
                  <div className="muted" style={{ fontSize: 12 }}>{u.email}</div>
                </div>
                <button
                  className="primary"
                  disabled={busy}
                  onClick={() => act(u.id, "approve", u.name)}
                >
                  Approve
                </button>
                <button className="ghost" disabled={busy} onClick={() => act(u.id, "reject", u.name)}>
                  Reject
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <h2>Password requests</h2>
        {pwReqs.length === 0 ? (
          <p className="empty">No pending password requests.</p>
        ) : (
          <div className="team-list">
            {pwReqs.map((r) => (
              <div className="team-row" key={r.id}>
                <div>
                  <div className="team-name">{r.userName}</div>
                  <div className="muted" style={{ fontSize: 12 }}>
                    {r.userEmail} · {r.type === "RESET" ? "forgot password" : "change request"}
                  </div>
                </div>
                <button className="primary" disabled={busy} onClick={() => pwAct(r, "approve")}>
                  {r.type === "RESET" ? "Set temp password" : "Approve"}
                </button>
                <button className="ghost" disabled={busy} onClick={() => pwAct(r, "reject")}>
                  Reject
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card">
        <h2>Everyone</h2>
        <div className="team-list">
          {roster.map((u) => (
            <div className="team-row" key={u.id}>
              <div>
                <div className="team-name">
                  {u.name} <span className="muted">@{u.handle}</span>
                </div>
                <div className="muted" style={{ fontSize: 12 }}>{u.email}</div>
              </div>
              <span className="muted">{capitalize(u.role)}</span>
              <span className="muted">{capitalize(u.status)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
