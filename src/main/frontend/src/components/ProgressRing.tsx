/** A small ring showing a work stage's completion — hours logged vs. estimated for
 *  crocheting/assembly (the stages with real time-tracking), or units done vs. total for
 *  packaging/shipment (piece-based, no hour estimate exists for those). Purely a visual
 *  summary; the underlying number is still edited via the time-tracking icons or the
 *  existing units-completed control next to it, not by interacting with the ring itself.
 *  Moved here from Order Tracker's own OrderDetailPage.tsx (round 4 review) since it's a
 *  generic percentage indicator with nothing order-specific about it. */
export function ProgressRing({ percent, size = 44 }: { percent: number; size?: number }) {
  const stroke = 5;
  const r = (size - stroke) / 2;
  const circumference = 2 * Math.PI * r;
  const clamped = Math.max(0, Math.min(100, percent));
  const offset = circumference * (1 - clamped / 100);
  return (
    <span style={{ position: "relative", width: size, height: size, display: "inline-block", flexShrink: 0 }}>
      <svg width={size} height={size} style={{ transform: "rotate(-90deg)" }} aria-hidden="true">
        <circle cx={size / 2} cy={size / 2} r={r} stroke="var(--border)" strokeWidth={stroke} fill="none" />
        <circle
          cx={size / 2} cy={size / 2} r={r}
          stroke={clamped >= 100 ? "var(--growth)" : "var(--accent)"}
          strokeWidth={stroke} fill="none" strokeLinecap="round"
          strokeDasharray={circumference} strokeDashoffset={offset}
          style={{ transition: "stroke-dashoffset 0.2s ease" }}
        />
      </svg>
      {/* Real DOM text, not just baked into the SVG or hidden behind the ring's own
          aria-hidden, so a screen reader still gets the percentage even without a
          sighted view of the ring itself. */}
      <span style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 10, fontWeight: 700 }}>
        {Math.round(clamped)}%
      </span>
    </span>
  );
}
