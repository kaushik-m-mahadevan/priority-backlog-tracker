import { useEffect, useState } from "react";
import { api, ApiError } from "../api/client";
import { useConfigCtx } from "../config/ConfigContext";
import { useGroups } from "../groups/GroupContext";
import { useGroupCategories } from "../groups/GroupCategoriesContext";
import { useTheme } from "../theme/ThemeContext";
import { useAuth } from "../auth/AuthContext";
import { useGroveSettings } from "../grove/GroveSettingsContext";
import { notifyItemsChanged } from "../lib/events";
import { TZ_CHOICES, getTzPref, setTzPref } from "../lib/tz";
import PasswordInput from "../components/PasswordInput";
import { Section } from "../components/Section";
import { Switch } from "../components/Switch";
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
  maxGroupsPerUser: string;
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
  ["maxGroupsPerUser", "Max groups per user"],
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
    maxGroupsPerUser: String(c.maxGroupsPerUser),
  };
}

export default function SettingsPage() {
  const { config, refresh } = useConfigCtx();
  const { currentGroup, refresh: refreshGroups } = useGroups();
  const { categories: groupCategories, refresh: refreshGroupCategories } = useGroupCategories();
  const { theme, setTheme } = useTheme();
  const { user, refresh: refreshAuth } = useAuth();
  const { enabled: animationsEnabled, setEnabled: setAnimationsEnabled } = useGroveSettings();
  const isAdmin = user?.role === "ADMIN";
  const [displayName, setDisplayName] = useState(user?.name ?? "");
  const [curPw, setCurPw] = useState("");
  const [newPw, setNewPw] = useState("");
  const [confPw, setConfPw] = useState("");
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

  if (!config || !draft) return <p className="empty">Loading…</p>;

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
      refreshGroups();
      refreshGroupCategories();
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
        maxGroupsPerUser: Number(draft!.maxGroupsPerUser),
        buriedPriorityLevels: buried,
        priorityValues: config!.priorityValues,
      }),
      "Configuration saved.",
    );
  }

  function saveName() {
    const n = displayName.trim();
    if (!n || n === user?.name) return;
    call(api.patch("/users/me", { name: n }).then(() => refreshAuth()), "Name updated.");
  }

  function toggleAnimations() {
    const next = !animationsEnabled;
    call(
      setAnimationsEnabled(next),
      next ? "Grove animations on." : "Grove animations off.",
    );
  }

  async function requestPasswordChange(replaceExisting = false) {
    if (newPw.length < 8 || newPw !== confPw) return;
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      await api.post("/auth/password-change", { currentPassword: curPw, newPassword: newPw, replaceExisting });
      setMsg("Sent to an admin — your password changes once they approve it.");
      setCurPw("");
      setNewPw("");
      setConfPw("");
    } catch (e) {
      // ad-7: same "replace pending request?" confirmation this app already uses
      // elsewhere for a single-pending-thing conflict, instead of a dead-end error.
      if (e instanceof ApiError && e.status === 409 && !replaceExisting) {
        setBusy(false);
        if (window.confirm("You already have a password request awaiting an admin. Replace it with this one?")) {
          await requestPasswordChange(true);
        }
        return;
      }
      setErr(e instanceof ApiError ? e.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  }

  function removeChip(kind: "categories" | "priorities", name: string, others: string[]) {
    const reassignTo = window.prompt(
      `If an item still uses "${name}", which ${kind === "categories" ? "category" : "priority"} should it move to?\n(${others.join(", ")})\nLeave blank if none use it.`,
      "",
    );
    if (reassignTo === null) return;
    const q = reassignTo ? `?reassignTo=${encodeURIComponent(reassignTo)}` : "";
    const path =
      kind === "categories"
        ? `/groups/${currentGroup?.id}/categories/${encodeURIComponent(name)}${q}`
        : `/config/priorities/${encodeURIComponent(name)}${q}`;
    call(api.delete(path), `"${name}" removed.`);
  }

  return (
    <div>
      <h1 className="page-title">Settings</h1>
      <p className="page-sub">Your account, plus each applet's own configuration below.</p>

      {err && <div className="error">{err}</div>}
      {msg && <div className="hint" style={{ color: "var(--growth)", marginBottom: 12 }}>{msg}</div>}

      <Section title="Your account" icon="👤" defaultOpen>
      <div className="grid cols-2">
        <div className="card">
          <h2>Theme</h2>
          <div style={{ display: "flex", gap: 8 }}>
            <button className={theme === "dusk" ? "primary" : ""} onClick={() => setTheme("dusk")}>
              Dusk
            </button>
            <button className={theme === "tide" ? "primary" : ""} onClick={() => setTheme("tide")}>
              Tide
            </button>
            <button className={theme === "brown" ? "primary" : ""} onClick={() => setTheme("brown")}>
              Brown
            </button>
          </div>
        </div>

        <div className="card">
          <h2>Your profile</h2>
          <div className="form-row">
            <label htmlFor="settings-display-name">Display name</label>
            <input id="settings-display-name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={80} />
          </div>
          <div className="form-row">
            <label htmlFor="settings-email">Email</label>
            <input id="settings-email" value={user?.email ?? ""} disabled />
          </div>
          <p className="hint" style={{ marginTop: 4 }}>
            Email is your login id and can't be changed here.
          </p>
          <div className="modal-actions" style={{ marginTop: 10 }}>
            <button
              className="primary"
              disabled={busy || !displayName.trim() || displayName.trim() === user?.name}
              onClick={saveName}
            >
              Save name
            </button>
          </div>
        </div>

        <div className="card">
          <h2>Password</h2>
          <div className="form-row">
            <label htmlFor="settings-current-password">Current password</label>
            <PasswordInput id="settings-current-password" value={curPw} onChange={setCurPw} autoComplete="current-password" />
          </div>
          <div className="form-row">
            <label htmlFor="settings-new-password">New password</label>
            <PasswordInput id="settings-new-password" value={newPw} onChange={setNewPw} autoComplete="new-password" />
          </div>
          <div className="form-row">
            <label htmlFor="settings-confirm-password">Confirm new password</label>
            <PasswordInput id="settings-confirm-password" value={confPw} onChange={setConfPw} autoComplete="new-password" />
            {confPw.length > 0 && confPw !== newPw && (
              <div className="hint bad">Passwords don't match.</div>
            )}
          </div>
          <p className="hint">
            Password changes go to an admin for approval — there's no instant reset, on
            purpose. Pick something you'll remember.
          </p>
          <div className="modal-actions" style={{ marginTop: 10 }}>
            <button
              className="primary"
              disabled={busy || !curPw || newPw.length < 8 || newPw !== confPw}
              onClick={() => requestPasswordChange()}
            >
              Request change
            </button>
          </div>
        </div>

        <div className="card">
          <h2>Display timezone</h2>
          <div className="form-row">
            <label htmlFor="settings-timezone">Show dates in</label>
            <select
              id="settings-timezone"
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
            Times are stored in a single global format behind the scenes; this only changes how they're displayed to you here.
          </p>
        </div>
      </div>
      </Section>

      {/* Everything below is Priority Backlog Tracker's own settings — Order Tracker
          (once it exists) gets its own section here, same shape, not a rewrite of this
          one (design: platform integration, "one shared Settings hub with sections"). */}
      <Section title="Priority Backlog Tracker" icon="🌲" defaultOpen>
      <div className="grid cols-2">
        <div className="card">
          <h2>Animations</h2>
          <Switch
            id="settings-animations"
            checked={animationsEnabled}
            onChange={toggleAnimations}
            disabled={busy}
            label="Let the grove react to the backlog"
            help="When on, the tree wilts and gets chopped as overdue and long-untouched items pile up in your current group, and recovers as you clear them. Off by default; the red overdue marker on rows is always shown regardless."
          />
        </div>

        {currentGroup && (
          <div className="card">
            <h2>Categories</h2>
            <p className="hint" style={{ marginTop: -4, marginBottom: 10 }}>
              Just for "{currentGroup.name}" — other groups keep their own list. Any member
              can edit these.
            </p>
            <div className="kv">
              {groupCategories.map((c) => (
                <span className="chip" key={c}>
                  {c}
                  <button
                    className="chip-x"
                    aria-label={`Remove ${c}`}
                    onClick={() =>
                      removeChip("categories", c, groupCategories.filter((x) => x !== c))
                    }
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
            <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
              <input
                aria-label="New category"
                placeholder="New category"
                value={newCat}
                onChange={(e) => setNewCat(e.target.value)}
              />
              <button
                disabled={busy || !newCat.trim()}
                onClick={() =>
                  call(
                    api.post(`/groups/${currentGroup.id}/categories`, { name: newCat.trim() }),
                    "Category added.",
                  ).then(() => setNewCat(""))
                }
              >
                Add
              </button>
            </div>
          </div>
        )}

        {isAdmin && (
          <>
            <div className="card">
              <h2>Ranking weights &amp; thresholds</h2>
              <div className="form-grid">
                {NUMERIC.map(([k, label]) => (
                  <div className="form-row" key={k}>
                    <label htmlFor={`ranking-${k}`}>{label}</label>
                    <input
                      id={`ranking-${k}`}
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
                  aria-label="New priority name"
                  placeholder="New priority"
                  value={newPrio}
                  onChange={(e) => setNewPrio(e.target.value)}
                  style={{ flex: 1 }}
                />
                <input
                  aria-label="New priority value"
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
          </>
        )}
      </div>
      </Section>
    </div>
  );
}
