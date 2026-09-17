import { useRef, useState } from "react";
import type { TimeLogEntryView } from "./types";

const PX_PER_QUARTER_HOUR = 8;
const STEP_HOURS = 0.25;
const MAX_HOURS = 12;
const LONG_PRESS_MS = 300;
const MOVE_THRESHOLD_PX = 6;

function roundToStep(hours: number): number {
  return Math.round(hours / STEP_HOURS) * STEP_HOURS;
}

export interface TimeStageControlProps {
  icon: string;
  label: string;
  /** Total estimated hours for this stage (order-level for Research, order- or
   *  variant-level for Crochet/Assembly depending on order type). */
  estimatedHours: number;
  /** Already-filtered to this stage (and variant, where relevant). */
  entries: TimeLogEntryView[];
  creatorName: (id: string) => React.ReactNode;
  onLog: (hours: number) => Promise<void>;
  onRemove: (entryId: string) => Promise<void>;
}

/** Long-press the icon, then drag up/down to set the number of hours — like a volume
 *  control, not a stepwise accumulator. The live value floats above the icon while
 *  dragging; releasing leaves it pending (nothing is sent yet), and a separate tap on ✓
 *  actually logs it. Only date (today), the current user, and the hours matter here — no
 *  notes, no backdating: log a whole week's worth in one entry whenever you get to it,
 *  not a running reconstruction of which day each session happened on. */
export function TimeStageControl({ icon, label, estimatedHours, entries, creatorName, onLog, onRemove }: TimeStageControlProps) {
  const [pending, setPending] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [dragValue, setDragValue] = useState(0);
  const [showLog, setShowLog] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const armedRef = useRef(false);
  const startYRef = useRef(0);
  const baseValueRef = useRef(0);
  const longPressTimerRef = useRef<number | null>(null);

  const clearLongPressTimer = () => {
    if (longPressTimerRef.current != null) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  };

  const arm = () => {
    armedRef.current = true;
    setDragging(true);
    setDragValue(baseValueRef.current);
  };

  const handlePointerDown = (e: React.PointerEvent<HTMLButtonElement>) => {
    e.preventDefault();
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
    armedRef.current = false;
    startYRef.current = e.clientY;
    baseValueRef.current = pending;
    clearLongPressTimer();
    longPressTimerRef.current = window.setTimeout(arm, LONG_PRESS_MS);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLButtonElement>) => {
    const deltaY = startYRef.current - e.clientY;
    if (!armedRef.current) {
      if (Math.abs(deltaY) < MOVE_THRESHOLD_PX) return;
      clearLongPressTimer();
      arm();
    }
    const hoursFromDrag = deltaY / PX_PER_QUARTER_HOUR / 4;
    const next = Math.min(MAX_HOURS, Math.max(0, roundToStep(baseValueRef.current + hoursFromDrag)));
    setDragValue(next);
  };

  const endGesture = () => {
    clearLongPressTimer();
    if (armedRef.current) {
      setPending(dragValue);
    }
    armedRef.current = false;
    setDragging(false);
  };

  const commitPending = async () => {
    if (pending <= 0) return;
    setError(null);
    try {
      await onLog(pending);
      setPending(0);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to log time");
    }
  };

  const loggedTotal = entries.reduce((sum, e) => sum + e.hours, 0);
  const delta = loggedTotal - estimatedHours;
  const displayValue = dragging ? dragValue : pending;

  return (
    <span style={{ display: "inline-flex", flexDirection: "column", gap: 4 }}>
    <span style={{ display: "inline-flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
      <span style={{ position: "relative" }}>
        {dragging && (
          <span className="time-track-live-label mono" aria-hidden="true">
            {dragValue.toFixed(2)}h
          </span>
        )}
        <button
          type="button"
          aria-label={`Long-press and drag to set ${label} time`}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={endGesture}
          onPointerCancel={endGesture}
          className="time-track-button time-track-button-inline"
          style={{ borderColor: dragging ? "var(--accent)" : undefined }}
        >
          <span
            aria-hidden="true"
            className="time-track-fill"
            style={{ height: `${Math.min(100, (displayValue / MAX_HOURS) * 100)}%` }}
          />
          <span className="time-track-icon">{icon}</span>
        </button>
      </span>

      <span className="muted" style={{ fontSize: 12 }}>
        {loggedTotal.toFixed(2)}h logged
        {estimatedHours > 0 && ` / ${estimatedHours.toFixed(2)}h estimated`}
        {estimatedHours > 0 && delta !== 0 && (
          <> · {delta > 0 ? `over by ${delta.toFixed(2)}h` : `under by ${Math.abs(delta).toFixed(2)}h`}</>
        )}
      </span>

      {pending > 0 && !dragging && (
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

      {error && <span className="error" style={{ fontSize: 12 }}>{error}</span>}

      {entries.length > 0 && (
        <button type="button" className="linkbtn" style={{ fontSize: 12 }} onClick={() => setShowLog((s) => !s)}>
          {showLog ? "hide log" : `log (${entries.length})`}
        </button>
      )}
    </span>

      {showLog && entries.length > 0 && (
        <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12 }} className="muted">
          {entries
            .slice()
            .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
            .map((e) => (
              <li key={e.entryId} style={{ marginBottom: 2 }}>
                {new Date(e.date).toLocaleDateString()} — {e.hours.toFixed(2)}h ({creatorName(e.loggedByCreatorId)}){" "}
                <button type="button" className="linkbtn" onClick={() => onRemove(e.entryId)}>
                  remove
                </button>
              </li>
            ))}
        </ul>
      )}
    </span>
  );
}
