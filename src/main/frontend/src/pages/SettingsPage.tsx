import { useEffect, useState } from "react";
import { api, ApiError } from "../api/client";
import { useConfigCtx } from "../config/ConfigContext";
import { useTheme } from "../theme/ThemeContext";
import { useUsers } from "../users/UsersContext";
import { notifyItemsChanged } from "../lib/events";
import { TZ_CHOICES, getTzPref, setTzPref } from "../lib/tz";
import type { AppConfig } from "../types";

type Draft = {
  priorityWeight: string;
  urgencyWeight: string;
  effortWeight: string;
  urgencyWindowDays: string;
  staleThresholdDays: string;
  buriedThresholdDays: string;
  defaultDueDateOffsetDays: string;
  effortCapDays: string;
};

const NUMERIC: [keyof Draft, string][] = [
  ["priorityWeight", "Priority weight"],
  ["urgencyWeight", "Urgency weight"],
  ["effortWeight", "Effort weight"],
  ["urgencyWindowDays", "Urgency window (days)"],
  ["staleThresholdDays", "Stale threshold (days)"],
  ["buriedThresholdDays", "Buried threshold (days)"],
  ["defaultDueDateOffsetDays", "Default due-date offset (days)"],
  ["effortCapDays", "Effort cap (days)"],
];

function toDraft(c: AppConfig): Draft {
  return {
    priorityWeight: String(c.priorityWeight),
    urgencyWeight: String(c.urgencyWeight),
    effortWeight: String(c.effortWeight),
    urgencyWindowDays: String(c.urgencyWindowDays),
    staleThresholdDays: String(c.staleThresholdDays),
    buriedThresholdDays: String(c.buriedThresholdDays),
    defaultDueDateOffsetDays: String(c.defaultDueDateOffsetDays),
    effortCapDays: String(c.effortCapDays),
  };
}

const ROLES = ["ADMIN", "USER"];
const BLANK_MEMBER = { name: "", email: "", password: "", role: "USER" };

export default function SettingsPage() {
  const { config, refresh } = useConfigCtx();
  const { theme, setTheme } = useTheme();
  const { users, refresh: refreshUsers } = useUsers();
  const [member, setMember] = useState(BLANK_MEMBER);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [buried, setBuried] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [newCat, setNewCat] = useState("");
  const [newPrio, setNewPrio] = useState("");
  const [newPrioVal, setNewPrioVal] = useState("2");
  const [tz, setTz] = useState(getTzPref());

  useEffect(() => {
    if (config) {
      setDraft(toDraft(config));
      setBuried(config.buriedPriorityLevels);
    }
  }, [config]);

  if (!config || !draft) return <p className="empty">Loading configuration…</p>;

  const weightSum =
    Number(draft.priorityWeight) + Number(draft.urgencyWeight) + Number(draft.effortWeight);
  const weightsOk = Math.abs(weightSum - 1) < 1e-9;

  async function call<T>(p: Promise<T>, ok: string) {
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      await p;
      setMsg(ok);
      refresh();
      notifyItemsChanged();
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  }

  function saveWeights() {
    if (!weightsOk) return;
    call(
      api.put("/config", {
        priorityWeight: Number(draft!.priorityWeight),
        urgencyWeight: Number(draft!.urgencyWeight),
        effortWeight: Number(draft!.effortWeight),
        urgencyWindowDays: Number(draft!.urgencyWindowDays),
        staleThresholdDays: Number(draft!.staleThresholdDays),
        buriedThresholdDays: Number(draft!.buriedThresholdDays),
        defaultDueDateOffsetDays: Number(draft!.defaultDueDateOffsetDays),
        effortCapDays: Number(draft!.effortCapDays),
        buriedPriorityLevels: buried,
        priorityValues: config!.priorityValues,
      }),
      "Configuration saved.",
    );
  }

  function addMember() {
    const m = { ...member, name: member.name.trim(), email: member.email.trim() };
    if (!m.name || !m.email || m.password.length < 8) {
      setErr("Name, email, and a password of at least 8 characters are required.");
      return;
    }
    call(api.post("/users", m), `${m.name} added.`).then(() => {
      setMember(BLANK_MEMBER);
      refreshUsers();
    });
  }

  function setRole(id: string, role: string) {
    call(api.patch(`/users/${id}`, { role }), "Role updated.").then(refreshUsers);
  }

  function resetPassword(id: string, name: string) {
    const password = window.prompt(`New password for ${name} (min 8 characters):`, "");
    if (password === null) return;
    if (password.length < 8) {
      setErr("Password must be at least 8 characters.");
      return;
    }
    call(api.patch(`/users/${id}`, { password }), `Password reset for ${name}.`);
  }

  function removeChip(kind: "categories" | "priorities", name: string, others: string[]) {
    const reassignTo = window.prompt(
      `If an item still uses "${name}", which ${kind === "categories" ? "category" : "priority"} should it move to?\n(${others.join(", ")})\nLeave blank if none use it.`,
      "",
    );
    if (reassignTo === null) return;
    const q = reassignTo ? `?reassignTo=${encodeURIComponent(reassignTo)}` : "";
    call(api.delete(`/config/${kind}/${encodeURIComponent(name)}${q}`), `"${name}" removed.`);
  }

  return (
    <div>
      <h1 className="page-title">Settings</h1>
      <p className="page-sub">Theme, and the ranking configuration (§7). Owner-only.</p>

      {err && <div className="error">{err}</div>}
      {msg && <div className="hint" style={{ color: "var(--growth)", marginBottom: 12 }}>{msg}</div>}

      <div className="grid cols-2">
        <div className="card" style={{ gridColumn: "1 / -1" }}>
          <h2>Team members</h2>
          <div className="team-list">
            {users.map((u) => (
              <div className="team-row" key={u.id}>
                <div>
                  <div className="team-name">{u.name}</div>
                  <div className="muted" style={{ fontSize: 12 }}>{u.email}</div>
                </div>
                <select value={u.role} onChange={(e) => setRole(u.id, e.target.value)} disabled={busy}>
                  {ROLES.map((r) => (
                    <option key={r} value={r}>
                      {r[0] + r.slice(1).toLowerCase()}
                    </option>
                  ))}
                </select>
                <button className="ghost" disabled={busy} onClick={() => resetPassword(u.id, u.name)}>
                  Reset password
                </button>
              </div>
            ))}
          </div>
          <div className="team-add">
            <input
              placeholder="Name"
              value={member.name}
              onChange={(e) => setMember({ ...member, name: e.target.value })}
            />
            <input
              placeholder="Email"
              type="email"
              value={member.email}
              onChange={(e) => setMember({ ...member, email: e.target.value })}
            />
            <input
              placeholder="Temp password (min 8)"
              value={member.password}
              onChange={(e) => setMember({ ...member, password: e.target.value })}
            />
            <select
              value={member.role}
              onChange={(e) => setMember({ ...member, role: e.target.value })}
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {r[0] + r.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
            <button className="primary" disabled={busy} onClick={addMember}>
              Add member
            </button>
          </div>
          <p className="hint" style={{ marginTop: 8 }}>
            New members sign in with the temp password and can be reset here. Owner-only (§8).
          </p>
        </div>

        <div className="card">
          <h2>Theme</h2>
          <div style={{ display: "flex", gap: 8 }}>
            <button className={theme === "dusk" ? "primary" : ""} onClick={() => setTheme("dusk")}>
              Dusk
            </button>
            <button className={theme === "tide" ? "primary" : ""} onClick={() => setTheme("tide")}>
              Tide
            </button>
          </div>
        </div>

        <div className="card">
          <h2>Display timezone</h2>
          <div className="form-row">
            <label>Show dates in</label>
            <select
              value={tz}
              onChange={(e) => {
                setTz(e.target.value);
                setTzPref(e.target.value);
                notifyItemsChanged();
              }}
            >
              {TZ_CHOICES.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>
          <p className="hint" style={{ marginTop: 8 }}>
            Instants are stored in UTC; this only changes how they read here (§22).
          </p>
        </div>

        <div className="card">
          <h2>Ranking weights &amp; thresholds</h2>
          <div className="form-grid">
            {NUMERIC.map(([k, label]) => (
              <div className="form-row" key={k}>
                <label>{label}</label>
                <input
                  type="number"
                  step={k.includes("Weight") ? "0.001" : "1"}
                  value={draft[k]}
                  onChange={(e) => setDraft({ ...draft, [k]: e.target.value })}
                />
              </div>
            ))}
          </div>
          <div className={`hint${weightsOk ? "" : " bad"}`}>
            weights sum to {weightSum.toFixed(3)} {weightsOk ? "✓" : "— must be exactly 1"}
          </div>
          <div className="modal-actions" style={{ marginTop: 12 }}>
            <button className="primary" disabled={busy || !weightsOk} onClick={saveWeights}>
              Save
            </button>
          </div>
        </div>

        <div className="card">
          <h2>Categories</h2>
          <div className="kv">
            {config.categories.map((c) => (
              <span className="chip" key={c}>
                {c}
                <button
                  className="chip-x"
                  aria-label={`Remove ${c}`}
                  onClick={() =>
                    removeChip("categories", c, config.categories.filter((x) => x !== c))
                  }
                >
                  ×
                </button>
              </span>
            ))}
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
            <input
              placeholder="New category"
              value={newCat}
              onChange={(e) => setNewCat(e.target.value)}
            />
            <button
              disabled={busy || !newCat.trim()}
              onClick={() =>
                call(api.post("/config/categories", { name: newCat.trim() }), "Category added.").then(
                  () => setNewCat(""),
                )
              }
            >
              Add
            </button>
          </div>
        </div>

        <div className="card">
          <h2>Priorities</h2>
          <div className="kv">
            {config.priorities.map((p) => (
              <span className="chip" key={p}>
                {p} = {config.priorityValues[p]}
                <button
                  className="chip-x"
                  aria-label={`Remove ${p}`}
                  onClick={() =>
                    removeChip("priorities", p, config.priorities.filter((x) => x !== p))
                  }
                >
                  ×
                </button>
              </span>
            ))}
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
            <input
              placeholder="New priority"
              value={newPrio}
              onChange={(e) => setNewPrio(e.target.value)}
              style={{ flex: 1 }}
            />
            <input
              type="number"
              min="1"
              value={newPrioVal}
              onChange={(e) => setNewPrioVal(e.target.value)}
              style={{ width: 70 }}
            />
            <button
              disabled={busy || !newPrio.trim()}
              onClick={() =>
                call(
                  api.post("/config/priorities", {
                    name: newPrio.trim(),
                    value: Number(newPrioVal),
                  }),
                  "Priority added.",
                ).then(() => setNewPrio(""))
              }
            >
              Add
            </button>
          </div>
          <p className="hint" style={{ marginTop: 8 }}>
            Higher weight = ranks higher. "Buried" levels: {buried.join(", ") || "none"}.
          </p>
        </div>
      </div>
    </div>
  );
}
