/** A compact two-option slide switch — replaces a pair of full-width buttons (e.g.
 *  Individual/Bulk, Existing/New customer) with one small pill, a sliding highlight
 *  behind whichever option is active. Moved here from Order Tracker's own
 *  OrderFormFields.tsx (round 4 review) since it's a generic, applet-agnostic control, not
 *  something specific to order forms — same reasoning as this folder's other small shared
 *  pieces (Badge, EffortIcon, PriorityMark). */
export function SlideToggle<T extends string>({
  value,
  options,
  onChange,
}: {
  value: T;
  options: [{ value: T; label: string }, { value: T; label: string }];
  onChange: (v: T) => void;
}) {
  const activeIndex = value === options[0].value ? 0 : 1;
  return (
    <div
      role="radiogroup"
      style={{
        position: "relative", display: "inline-flex", background: "var(--bg-elev-2)",
        border: "1px solid var(--border)", borderRadius: 999, padding: 3,
      }}
    >
      <div
        aria-hidden="true"
        style={{
          position: "absolute", top: 3, bottom: 3,
          left: `calc(${activeIndex * 50}% + 3px)`,
          width: "calc(50% - 6px)",
          background: "var(--accent)", borderRadius: 999,
          transition: "left 0.18s ease",
        }}
      />
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={o.value === value}
          className={o.value === value ? "slide-toggle-active" : undefined}
          onClick={() => onChange(o.value)}
          style={{
            position: "relative", border: "none", background: "transparent",
            padding: "6px 16px", borderRadius: 999, fontSize: 13, fontWeight: 600,
            minWidth: 120, cursor: "pointer",
          }}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
