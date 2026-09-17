import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { TimeLogEntryView } from "./types";

const STEP_HOURS = 0.25;
const MAX_HOURS = 12;
const TRACK_HEIGHT = 160;
const KNOB_SIZE = 18;
const POPOVER_HALF_WIDTH = 60;
const VIEWPORT_MARGIN = 8;

function roundToStep(hours: number): number {
  return Math.round(hours / STEP_HOURS) * STEP_HOURS;
}

function clampHours(hours: number): number {
  return Math.min(MAX_HOURS, Math.max(0, roundToStep(hours)));
}

export interface TimeStageControlProps {
  icon: string;
  label: string;
  /** Total estimated hours for this stage (order-level for Research, order- or
   *  variant-level for Crochet/Assembly depending on order type). */
  estimatedHours: number;
  /** Already-filtered to this stage (and variant, where relevant) — used only for the
   *  running total shown next to the icon. Full entry-by-entry history lives in one
   *  consolidated, filterable section at the end of the order page, not here. */
  entries: TimeLogEntryView[];
  onLog: (hours: number) => Promise<void>;
}

/** Tap the icon to open a vertical slider — click the track or drag the knob to set the
 *  number of hours, exactly like a volume control, with the value always visible next to
 *  the knob rather than a hidden gesture. A separate confirm tap actually logs it; nothing
 *  is sent while you're still adjusting. Only date (today), the current user, and the
 *  hours matter here — no notes, no backdating: log a whole week's worth in one entry
 *  whenever you get to it, not a running reconstruction of which day each session
 *  happened on.
 *
 *  Every confirm adds a brand-new entry — logging more time later the same day (e.g.
 *  plans changed and you crocheted three more hours) is a second, separate entry, not a
 *  merge into an earlier one. The slider always starts from zero: it's for logging a new
 *  block of time, not editing a past one — edit/remove a specific entry from the Time
 *  History section instead. */
export function TimeStageControl({ icon, label, estimatedHours, entries, onLog }: TimeStageControlProps) {
  const [open, setOpen] = useState(false);
  const [anchor, setAnchor] = useState<{ left: number; bottom: number } | null>(null);
  const [value, setValue] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const containerRef = useRef<HTMLSpanElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
  const trackRef = useRef<HTMLDivElement>(null);

  const openPopover = () => {
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const left = Math.min(
      window.innerWidth - VIEWPORT_MARGIN - POPOVER_HALF_WIDTH,
      Math.max(VIEWPORT_MARGIN + POPOVER_HALF_WIDTH, rect.left + rect.width / 2)
    );
    setAnchor({ left, bottom: window.innerHeight - rect.top });
    setValue(0);
    setOpen(true);
  };

  useEffect(() => {
    if (!open) return;
    const onOutsideClick = (e: MouseEvent) => {
      const target = e.target as Node;
      if (
        containerRef.current && !containerRef.current.contains(target) &&
        popoverRef.current && !popoverRef.current.contains(target)
      ) {
        setOpen(false);
      }
    };
    const onScrollOrResize = () => setOpen(false);
    document.addEventListener("pointerdown", onOutsideClick);
    window.addEventListener("scroll", onScrollOrResize, true);
    window.addEventListener("resize", onScrollOrResize);
    return () => {
      document.removeEventListener("pointerdown", onOutsideClick);
      window.removeEventListener("scroll", onScrollOrResize, true);
      window.removeEventListener("resize", onScrollOrResize);
    };
  }, [open]);

  const valueFromClientY = (clientY: number): number => {
    const track = trackRef.current;
    if (!track) return value;
    const rect = track.getBoundingClientRect();
    const fromBottom = rect.bottom - clientY;
    return clampHours((fromBottom / rect.height) * MAX_HOURS);
  };

  const handleTrackPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
    setDragging(true);
    setValue(valueFromClientY(e.clientY));
  };

  const handleTrackPointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!dragging) return;
    setValue(valueFromClientY(e.clientY));
  };

  const stopDragging = () => setDragging(false);

  const handleTrackKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === "ArrowUp") {
      e.preventDefault();
      setValue((v) => clampHours(v + STEP_HOURS));
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      setValue((v) => clampHours(v - STEP_HOURS));
    }
  };

  const confirm = async () => {
    if (value <= 0) {
      setOpen(false);
      return;
    }
    setError(null);
    try {
      await onLog(value);
      setValue(0);
      setOpen(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to log time");
    }
  };

  const cancel = () => {
    setValue(0);
    setOpen(false);
  };

  const loggedTotal = entries.reduce((sum, e) => sum + e.hours, 0);
  const delta = loggedTotal - estimatedHours;
  const knobOffset = (value / MAX_HOURS) * TRACK_HEIGHT;

  return (
    <span ref={containerRef} style={{ display: "inline-flex", alignItems: "center", gap: 10, flexWrap: "wrap", position: "relative" }}>
      <span style={{ position: "relative" }}>
        <button
          ref={triggerRef}
          type="button"
          aria-label={`Log ${label} time`}
          aria-expanded={open}
          onClick={() => (open ? setOpen(false) : openPopover())}
          className="time-track-button time-track-button-inline"
          style={{ borderColor: open ? "var(--accent)" : undefined }}
        >
          <span className="time-track-icon">{icon}</span>
        </button>

        {open && anchor && createPortal(
          <div
            ref={popoverRef}
            className="time-slider-popover"
            style={{ position: "fixed", left: anchor.left, bottom: anchor.bottom, transform: "translateX(-50%)" }}
          >
            <div className="time-slider-value mono">{value.toFixed(2)}h</div>
            <div
              ref={trackRef}
              className="time-slider-track"
              role="slider"
              tabIndex={0}
              aria-label={`${label} hours`}
              aria-valuemin={0}
              aria-valuemax={MAX_HOURS}
              aria-valuenow={value}
              onPointerDown={handleTrackPointerDown}
              onPointerMove={handleTrackPointerMove}
              onPointerUp={stopDragging}
              onPointerCancel={stopDragging}
              onKeyDown={handleTrackKeyDown}
              style={{ height: TRACK_HEIGHT }}
            >
              <div className="time-slider-fill" style={{ height: knobOffset }} />
              <div
                className="time-slider-knob"
                aria-hidden="true"
                style={{ bottom: knobOffset - KNOB_SIZE / 2, width: KNOB_SIZE, height: KNOB_SIZE }}
              />
            </div>
            <div className="time-slider-actions">
              <button type="button" aria-label={`Confirm ${label} time`} onClick={confirm}>
                ✓
              </button>
              <button type="button" aria-label={`Cancel ${label} time`} onClick={cancel}>
                ×
              </button>
            </div>
          </div>,
          document.body
        )}
      </span>

      <span className="muted" style={{ fontSize: 12 }}>
        {loggedTotal.toFixed(2)}h logged
        {estimatedHours > 0 && ` / ${estimatedHours.toFixed(2)}h estimated`}
        {estimatedHours > 0 && delta !== 0 && (
          <> · {delta > 0 ? `over by ${delta.toFixed(2)}h` : `under by ${Math.abs(delta).toFixed(2)}h`}</>
        )}
      </span>

      {error && <span className="error" style={{ fontSize: 12 }}>{error}</span>}
    </span>
  );
}
