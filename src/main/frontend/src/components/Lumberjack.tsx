/**
 * A small colored mascot lumberjack — chosen over the app's monochrome line-icon
 * language on purpose (opt-in "the grove suffers" animation). Fixed real-world colors,
 * same idea as the emoji `Creature` avatars: it doesn't retint with the Dusk/Tide
 * theme toggle. Drawn in its own 40x56 unit space and embedded as a nested `<svg>` so
 * callers can place/size it in the Grove's own coordinate system without redoing any
 * of this path data.
 *
 * The swing is three drawn poses cut between on the beat, the way a sprite sheet does
 * it, rather than one shape rotated or nudged: wound back across the body and held,
 * one fast pass frame, then impact held against the trunk. A chopping swing travels
 * around the spine, and no in-plane rotation of a flat side view can express that —
 * separate poses can. Only `opacity` animates, so nothing here ever fights an SVG
 * `transform` attribute (see the Grove wilt-animation postmortem: a CSS transform on
 * an element that also carries a transform attribute drops the attribute outright).
 *
 * Geometry worth keeping straight: the axe head is mounted perpendicular to the shaft,
 * and each pose's `<g transform>` is rotated so the head's own local "up the handle"
 * axis lines up with that pose's shaft, which is what keeps the handle reading as one
 * straight stick passing through the eye.
 */
const ASPECT = 40 / 56;

/** Blade + its edge highlight, in the axe's own space: handle-end flat at x=0, edge out at -x. */
function Head() {
  return (
    <>
      <path
        d="M0,-2 Q-4,-1 -10,-6 Q-14,0 -10,6 Q-4,1 0,2 Z"
        fill="var(--lj-axe)"
        stroke="#1c1c22"
        strokeWidth="0.45"
      />
      <path
        d="M-10,-6 Q-14,0 -10,6"
        fill="none"
        stroke="var(--lj-axe-hi)"
        strokeWidth="0.5"
        strokeLinecap="round"
      />
    </>
  );
}

function Body() {
  return (
    <>
      <rect x="9" y="42" width="4" height="9" rx="2" fill="var(--lj-coat-dk)" />
      <rect x="15" y="42" width="4" height="9" rx="2" fill="var(--lj-coat-dk)" />
      <ellipse cx="9.5" cy="51.5" rx="3.4" ry="2" fill="var(--lj-boot)" />
      <ellipse cx="17.5" cy="51.5" rx="3.4" ry="2" fill="var(--lj-boot)" />
      <rect x="6" y="27" width="16" height="17" rx="7" fill="var(--lj-coat)" />
      <circle cx="14" cy="15" r="9.5" fill="var(--lj-skin)" />
      <path
        d="M5 14 C4.3 7.5 8.6 3.8 14 3.8 C19.4 3.8 23.7 7.5 23 14 C19.8 10.8 8.2 10.8 5 14 Z"
        fill="var(--lj-coat-dk)"
      />

      {/* A — wound back across the body, held */}
      <g className="pose pose-a">
        <path
          d="M19.2 32.6 C18 34.6 15.8 36.2 14.3 37.25"
          stroke="var(--lj-coat-dk)"
          strokeWidth="3.4"
          strokeLinecap="round"
          fill="none"
        />
        <path d="M15.6 38.75 L5 26.75" stroke="var(--lj-handle)" strokeWidth="1.8" strokeLinecap="round" />
        <g transform="translate(6.71 29.26) rotate(-41.3) scale(-0.65 0.65)">
          <Head />
        </g>
        <path
          d="M19.8 30.5 C17.5 31.6 13.8 33.4 11.9 34.55"
          stroke="var(--lj-coat)"
          strokeWidth="3.6"
          strokeLinecap="round"
          fill="none"
        />
        <circle cx="14.3" cy="37.25" r="1.6" fill="var(--lj-skin)" />
        <circle cx="11.9" cy="34.55" r="1.7" fill="var(--lj-skin)" />
      </g>

      {/* B — the pass, on screen for a single fast beat */}
      <g className="pose pose-b">
        <path
          d="M19.2 32.6 C20.2 34.4 21 36 21.65 37"
          stroke="var(--lj-coat-dk)"
          strokeWidth="3.4"
          strokeLinecap="round"
          fill="none"
        />
        <path
          d="M11.5 23.5 Q16.5 21.6 20.6 23.8"
          stroke="var(--text-dim)"
          strokeWidth="0.7"
          fill="none"
          opacity=".45"
          strokeLinecap="round"
        />
        <path
          d="M13.2 28.6 Q17.6 27.2 21.2 28.8"
          stroke="var(--text-dim)"
          strokeWidth="0.6"
          fill="none"
          opacity=".3"
          strokeLinecap="round"
        />
        <path d="M21.5 39 L22.7 22.5" stroke="var(--lj-handle)" strokeWidth="1.8" strokeLinecap="round" />
        <g transform="translate(22.11 25.47) rotate(4.2) scale(-0.65 0.65)">
          <Head />
        </g>
        <path
          d="M19.8 30.5 C20.8 31.5 21.5 32.4 21.9 33.4"
          stroke="var(--lj-coat)"
          strokeWidth="3.6"
          strokeLinecap="round"
          fill="none"
        />
        <circle cx="21.65" cy="37" r="1.6" fill="var(--lj-skin)" />
        <circle cx="21.9" cy="33.4" r="1.7" fill="var(--lj-skin)" />
      </g>

      {/* C — impact, edge buried in the trunk, held */}
      <g className="pose pose-c">
        <path
          d="M19.2 32.6 C21 34 22.2 35.6 23.07 36.76"
          stroke="var(--lj-coat-dk)"
          strokeWidth="3.4"
          strokeLinecap="round"
          fill="none"
        />
        <path d="M22.6 38.5 L26.8 23" stroke="var(--lj-handle)" strokeWidth="1.8" strokeLinecap="round" />
        <path d="M22.21 38.4 L26.41 22.9" stroke="var(--lj-handle-hi)" strokeWidth="0.5" strokeLinecap="round" />
        <g transform="translate(25.62 25.8) rotate(15.1) scale(-0.65 0.65)">
          <Head />
        </g>
        <path
          d="M19.8 30.5 C21.6 31.2 23 32.3 24.06 33.09"
          stroke="var(--lj-coat)"
          strokeWidth="3.6"
          strokeLinecap="round"
          fill="none"
        />
        <circle cx="23.07" cy="36.76" r="1.6" fill="var(--lj-skin)" />
        <circle cx="24.06" cy="33.09" r="1.7" fill="var(--lj-skin)" />
      </g>

      <g className="lj-spark">
        <path
          d="M33.9 24.7 l0.9 1.7 M37.4 27.6 l-1.9 0.7 M34.3 32 l0.9 -1.9"
          stroke="var(--accent)"
          strokeWidth="1"
          strokeLinecap="round"
        />
      </g>
    </>
  );
}

export function Lumberjack({
  x,
  y,
  height,
  flip = false,
  delay,
}: {
  /** top-left corner, in the Grove's own coordinate units */
  x: number;
  y: number;
  /** rendered height, in the Grove's own coordinate units; width follows the aspect ratio */
  height: number;
  flip?: boolean;
  /** offsets this one's swing so two of them don't chop in lockstep */
  delay?: string;
}) {
  const width = height * ASPECT;
  const svg = (
    <svg x={0} y={0} width={width} height={height} viewBox="0 0 40 56" aria-hidden="true">
      <Body />
    </svg>
  );
  // Drawn facing right, swinging at a trunk off its right shoulder. Flipped, the mirror
  // puts the trunk on its left — so `flip` is really "which side of the trunk it stands on".
  const style = delay ? ({ "--lj-delay": delay } as React.CSSProperties) : undefined;
  if (!flip) {
    return (
      <g transform={`translate(${x} ${y})`} style={style}>
        {svg}
      </g>
    );
  }
  return (
    <g transform={`translate(${x + width} ${y}) scale(-1 1)`} style={style}>
      {svg}
    </g>
  );
}
