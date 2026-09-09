import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "../api/client";

interface Row {
  id: string;
  name: string;
  handle: string;
  email: string;
  role: string;
  status: string;
}

export default function AdminPage() {
  const [pending, setPending] = useState<Row[]>([]);
  const [roster, setRoster] = useState<Row[]>([]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(() => {
    Promise.all([
      api.get<Row[]>("/admin/pending-users"),
      api.get<Row[]>("/admin/users"),
    ])
      .then(([p, r]) => {
        setPending(p);
        setRoster(r);
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
              <span className="muted">{u.role[0] + u.role.slice(1).toLowerCase()}</span>
              <span className="muted">{u.status[0] + u.status.slice(1).toLowerCase()}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
