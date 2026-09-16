import { useEffect, useRef, useState } from "react";
import type { TimeLogEntryView } from "./types";

const HOLD_STEP_MS = 500;
const HOLD_STEP_HOURS = 0.25;
const HOLD_CAP_HOURS = 4;

export interface TimeStageControlProps {
  icon: string;
  label: string;
  /** Total estimated hours for this stage (order-level for Research, order- or
   *  variant-level for Crochet/Assembly depending on order type). */
  estimatedHours: number;
  /** Already-filtered to this stage (and variant, where relevant). */
  entries: TimeLogEntryView[];
  creatorName: (id: string) => React.ReactNode;
  onLog: (hours: number, date: string | null, note: string | null) => Promise<void>;
  onRemove: (entryId: string) => Promise<void>;
}

/** Press-and-hold to add quarter-hour increments (works identically for touch long-press
 *  and mouse click-hold via pointer events — no separate mobile/desktop logic needed).
 *  Holding fills a gauge behind the icon and accumulates a "pending" amount, capped at
 *  {@link HOLD_CAP_HOURS} per hold so an accidentally-long press can't log something
 *  absurd; releasing and holding again keeps adding on top. Nothing is sent until the
 *  tick button is pressed (design decision: commit needs an explicit confirm, not
 *  immediate-on-release) — the × button discards the pending amount instead. A "+ Log
 *  time" link covers backdated/bulk catch-up entries the gesture isn't meant for. */
export function TimeStageControl({ icon, label, estimatedHours, entries, creatorName, onLog, onRemove }: TimeStageControlProps) {
  const [pending, setPending] = useState(0);
  const [holding, setHolding] = useState(false);
  const [showLog, setShowLog] = useState(false);
  const [showManual, setShowManual] = useState(false);
  const [manualHours, setManualHours] = useState("");
  const [manualDate, setManualDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [manualNote, setManualNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const intervalRef = useRef<number | null>(null);

  const stopHold = () => {
    setHolding(false);
    if (intervalRef.current != null) {
      window.clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  };

  useEffect(() => stopHold, []);

  const startHold = (e: React.PointerEvent) => {
    e.preventDefault();
    if (intervalRef.current != null) return;
    setHolding(true);
    setPending((p) => Math.min(HOLD_CAP_HOURS, p + HOLD_STEP_HOURS));
    intervalRef.current = window.setInterval(() => {
      setPending((p) => Math.min(HOLD_CAP_HOURS, p + HOLD_STEP_HOURS));
    }, HOLD_STEP_MS);
  };

  const commitPending = async () => {
    if (pending <= 0) return;
    setError(null);
    try {
      await onLog(pending, null, null);
      setPending(0);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to log time");
    }
  };

  const submitManual = async (e: React.FormEvent) => {
    e.preventDefault();
    const hours = Number(manualHours);
    if (!hours || hours <= 0) {
      setError("Enter a positive number of hours");
      return;
    }
    setError(null);
    try {
      await onLog(hours, manualDate ? new Date(manualDate).toISOString() : null, manualNote.trim() || null);
      setManualHours("");
      setManualNote("");
      setShowManual(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to log time");
    }
  };

  const loggedTotal = entries.reduce((sum, e) => sum + e.hours, 0);
  const delta = loggedTotal - estimatedHours;
  const pct = estimatedHours > 0 ? Math.min(150, Math.round((loggedTotal / estimatedHours) * 100)) : 0;
  const over = estimatedHours > 0 && loggedTotal > estimatedHours;

  return (
    <div className="card" style={{ background: "var(--bg-elev-2)" }}>
      {error && <div className="error" style={{ marginBottom: 8 }}>{error}</div>}
      <div className="toolbar" style={{ alignItems: "center", gap: 12 }}>
        <button
          type="button"
          aria-label={`Hold to add ${label} time`}
          onPointerDown={startHold}
          onPointerUp={stopHold}
          onPointerLeave={stopHold}
          onPointerCancel={stopHold}
          className="time-track-button"
          style={{ borderColor: holding ? "var(--accent)" : undefined }}
        >
          <span
            aria-hidden="true"
            className="time-track-fill"
            style={{ height: `${Math.min(100, (pending / HOLD_CAP_HOURS) * 100)}%` }}
          />
          <span className="time-track-icon">{icon}</span>
        </button>
        <div style={{ flex: 1, minWidth: 140 }}>
          <div style={{ fontWeight: 600 }}>{label}</div>
          <div className="muted" style={{ fontSize: 12 }}>
            {loggedTotal.toFixed(2)}h logged
            {estimatedHours > 0 && ` / ${estimatedHours.toFixed(2)}h estimated`}
            {estimatedHours > 0 && delta !== 0 && (
              <> · {delta > 0 ? `over by ${delta.toFixed(2)}h` : `under by ${Math.abs(delta).toFixed(2)}h`}</>
            )}
          </div>
          {estimatedHours > 0 && (
            <div className="order-progress-track" style={{ marginTop: 6 }}>
              <div
                className="order-progress-fill"
                style={{ width: `${pct}%`, background: over ? "var(--urgent)" : undefined }}
              />
            </div>
          )}
        </div>
        {pending > 0 && (
          <span style={{ display: "inline-flex", gap: 4, alignItems: "center" }}>
            <span className="mono">{pending.toFixed(2)}h</span>
            <button type="button" aria-label={`Confirm ${label} time`} onClick={commitPending}>
              ✓
            </button>
            <button type="button" aria-label={`Cancel pending ${label} time`} onClick={() => setPending(0)}>
              ×
            </button>
          </span>
        )}
      </div>

      <div className="toolbar" style={{ marginTop: 8 }}>
        <button type="button" className="linkbtn" onClick={() => setShowManual((s) => !s)}>
          + Log time
        </button>
        {entries.length > 0 && (
          <button type="button" className="linkbtn" onClick={() => setShowLog((s) => !s)}>
            {showLog ? "hide log" : `log (${entries.length})`}
          </button>
        )}
      </div>

      {showManual && (
        <form onSubmit={submitManual} className="form-grid" style={{ marginTop: 8 }}>
          <div className="form-row">
            <label htmlFor={`${label}-manual-hours`} className="muted" style={{ fontSize: 11 }}>
              Hours
            </label>
            <input
              id={`${label}-manual-hours`}
              type="number"
              min={0.25}
              step={0.25}
              value={manualHours}
              onChange={(e) => setManualHours(e.target.value)}
              required
            />
          </div>
          <div className="form-row">
            <label htmlFor={`${label}-manual-date`} className="muted" style={{ fontSize: 11 }}>
              Date
            </label>
            <input
              id={`${label}-manual-date`}
              type="date"
              value={manualDate}
              onChange={(e) => setManualDate(e.target.value)}
            />
          </div>
          <div className="form-row">
            <label htmlFor={`${label}-manual-note`} className="muted" style={{ fontSize: 11 }}>
              Note (optional)
            </label>
            <input id={`${label}-manual-note`} value={manualNote} onChange={(e) => setManualNote(e.target.value)} />
          </div>
          <div style={{ alignSelf: "flex-end" }}>
            <button className="primary" type="submit">
              Add
            </button>
          </div>
        </form>
      )}

      {showLog && entries.length > 0 && (
        <ul style={{ margin: "8px 0 0", paddingLeft: 18, fontSize: 12 }} className="muted">
          {entries
            .slice()
            .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
            .map((e) => (
              <li key={e.entryId} style={{ marginBottom: 2 }}>
                {new Date(e.date).toLocaleDateString()} — {e.hours.toFixed(2)}h ({creatorName(e.loggedByCreatorId)})
                {e.note && <> — {e.note}</>}{" "}
                <button type="button" className="linkbtn" onClick={() => onRemove(e.entryId)}>
                  remove
                </button>
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}
