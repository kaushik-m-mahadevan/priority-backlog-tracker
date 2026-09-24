/** mb-6: crafting/assembly/research time was hours-only at a 15-minute step — no way to
 *  enter a genuine 10-minute task without either rounding up to 15 or down to 0. Splits a
 *  single decimal-hours value into a whole-hours field and a 0–59-minutes field, so a
 *  duration under an hour is typed directly ("10" minutes) instead of computed by hand
 *  (0.1667h). Combines back into the same decimal-hours number every other part of the
 *  app already expects — no backend or domain-model change needed. */
export function HoursMinutesInput({
  idPrefix,
  hours,
  onChange,
  disabled,
}: {
  idPrefix: string;
  hours: number;
  onChange: (hours: number) => void;
  disabled?: boolean;
}) {
  const safeHours = Number.isFinite(hours) && hours > 0 ? hours : 0;
  const wholeHours = Math.floor(safeHours);
  // round rather than floor the remainder so e.g. 0.999999h (float noise) doesn't display
  // as "0h 59m" one tick away from "1h 0m"
  let minutes = Math.round((safeHours - wholeHours) * 60);
  let displayHours = wholeHours;
  if (minutes === 60) {
    minutes = 0;
    displayHours += 1;
  }

  const setHours = (h: number) => onChange(Math.max(0, h) + minutes / 60);
  const setMinutes = (m: number) => onChange(displayHours + Math.min(59, Math.max(0, m)) / 60);

  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
      <label htmlFor={`${idPrefix}-h`} className="sr-only">
        Hours
      </label>
      <input
        id={`${idPrefix}-h`}
        type="number"
        min={0}
        step={1}
        value={displayHours}
        disabled={disabled}
        onChange={(e) => setHours(Number(e.target.value))}
        style={{ width: 56 }}
      />
      <span className="muted" style={{ fontSize: 11 }}>
        h
      </span>
      <label htmlFor={`${idPrefix}-m`} className="sr-only">
        Minutes
      </label>
      <input
        id={`${idPrefix}-m`}
        type="number"
        min={0}
        max={59}
        step={1}
        value={minutes}
        disabled={disabled}
        onChange={(e) => setMinutes(Number(e.target.value))}
        style={{ width: 48 }}
      />
      <span className="muted" style={{ fontSize: 11 }}>
        m
      </span>
    </span>
  );
}
