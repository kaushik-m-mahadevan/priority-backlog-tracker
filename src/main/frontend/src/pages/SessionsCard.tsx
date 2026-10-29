import { FormEvent, useCallback, useEffect, useState } from "react";
import { api, ApiError } from "../api/client";
import { formatDateTime } from "../lib/format";

interface SessionRow {
  id: string;
  userId: string;
  userName: string;
  userEmail: string;
  loginAt: string | null;
  lastActivityAt: string | null;
  logoutAt: string | null;
  logoutReason: "SIGN_OUT" | "INACTIVITY_TIMEOUT" | "TOKEN_EXPIRED" | null;
  active: boolean;
  durationSeconds: number | null;
}

export function formatDuration(seconds: number | null): string {
  if (seconds === null || seconds < 0) return "—";
  if (seconds < 60) return `${seconds}s`;
  const m = Math.floor(seconds / 60);
  if (m < 60) return `${m}m`;
  return `${Math.floor(m / 60)}h ${m % 60}m`;
}

const REASON_LABEL: Record<string, string> = {
  SIGN_OUT: "Signed out",
  INACTIVITY_TIMEOUT: "Timed out (inactive)",
  TOKEN_EXPIRED: "Login expired",
};

/** Admin Console: who logged in and out when, plus the inactivity timeout that ends a
 *  session on its own. */
export default function SessionsCard() {
  const [rows, setRows] = useState<SessionRow[]>([]);
  const [timeout, setTimeoutValue] = useState("");
  const [saved, setSaved] = useState<number | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const load = useCallback(() => {
    Promise.all([
      api.get<SessionRow[]>("/admin/sessions?limit=100"),
      api.get<{ timeoutMinutes: number }>("/admin/session-policy"),
    ])
      .then(([sessions, policy]) => {
        setRows(sessions);
        setSaved(policy.timeoutMinutes);
        setTimeoutValue((cur) => (cur === "" ? String(policy.timeoutMinutes) : cur));
      })
      .catch((e) => setErr(e instanceof ApiError ? e.message : "Could not load sessions"));
  }, []);

  useEffect(load, [load]);

  async function save(e: FormEvent) {
    e.preventDefault();
    setErr(null);
    setMsg(null);
    try {
      const res = await api.put<{ timeoutMinutes: number }>("/admin/session-policy", {
        timeoutMinutes: Number(timeout),
      });
      setSaved(res.timeoutMinutes);
      setTimeoutValue(String(res.timeoutMinutes));
      setMsg(
        res.timeoutMinutes === 0
          ? "Automatic sign-out is off. People stay signed in until they sign out or their login expires."
          : "Timeout saved. It applies to everyone, including people already signed in.",
      );
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : "Could not save");
    }
  }

  const changed = timeout !== "" && Number(timeout) !== saved;

  return (
    <div className="card" style={{ marginTop: 16 }}>
      <h2>Sessions</h2>
      <form onSubmit={save} className="toolbar" style={{ marginBottom: 12 }}>
        <label htmlFor="session-timeout">Sign people out after</label>
        <input
          id="session-timeout"
          type="number"
          min={0}
          max={1440}
          style={{ width: 90 }}
          value={timeout}
          onChange={(e) => setTimeoutValue(e.target.value)}
        />
        <span className="muted">minutes without activity (0 = never)</span>
        <button className="primary" type="submit" disabled={!changed}>
          Save
        </button>
        <button className="ghost" type="button" onClick={load}>
          Refresh
        </button>
      </form>
      {err && <div className="error">{err}</div>}
      {msg && <div className="hint" style={{ color: "var(--growth)", marginBottom: 8 }}>{msg}</div>}

      {rows.length === 0 ? (
        <p className="empty">No sessions recorded yet.</p>
      ) : (
        <div className="table-wrap">
          <table className="ot-table">
            <thead>
              <tr>
                <th>User</th>
                <th>Logged in</th>
                <th>Logged out</th>
                <th>Length</th>
                <th>How it ended</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s) => (
                <tr key={s.id}>
                  <td>
                    {s.userName}
                    <div className="muted" style={{ fontSize: 12 }}>{s.userEmail}</div>
                  </td>
                  <td>{formatDateTime(s.loginAt)}</td>
                  <td>{s.active ? <span className="badge">Active</span> : formatDateTime(s.logoutAt)}</td>
                  <td>{formatDuration(s.durationSeconds)}</td>
                  <td>{s.active ? "—" : REASON_LABEL[s.logoutReason ?? ""] ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
