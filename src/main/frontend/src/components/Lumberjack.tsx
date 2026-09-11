/**
 * A small colored mascot lumberjack — chosen over the app's monochrome line-icon
 * language on purpose (opt-in "the grove suffers" animation). Fixed real-world colors,
 * same idea as the emoji `Creature` avatars: it doesn't retint with the Dusk/Tide
 * theme toggle. Drawn in its own 40x56 unit space and embedded as a nested `<svg>` so
 * callers can place/size it in the Grove's own coordinate system without redoing any
 * of this path data.
 *
 * The swinging arm+axe lives in its own group (`lj-arm-swing`, animated in index.css)
 * with no SVG `transform` attribute of its own — putting a CSS animation on the SAME
 * element that also carries an SVG transform attribute makes the browser drop the
 * attribute's translate/scale entirely (see the Grove wilt-animation postmortem).
 */
const ASPECT = 40 / 56;

function Body() {
  return (
    <>
      {/* boots */}
      <rect x="14" y="42.5" width="6.4" height="5.2" rx="2" fill="var(--lj-boot)" />
      <rect x="19.8" y="42.5" width="6.4" height="5.2" rx="2" fill="var(--lj-boot)" />
      <path d="M14 46.5 H20.4 M19.8 46.5 H26.2" stroke="var(--lj-boot-dk)" strokeWidth=".6" />
      <g stroke="var(--lj-skin-sh)" strokeWidth=".55" opacity=".8">
        <path d="M14.8 44 L16 45 M16.5 43.4 L17.8 44.6" />
        <path d="M21 44 L22.2 45 M22.7 43.4 L24 44.6" />
      </g>
      {/* legs */}
      <rect x="15" y="31.5" width="4.4" height="12" rx="1.6" fill="var(--lj-pants)" />
      <rect x="20.6" y="31.5" width="4.4" height="12" rx="1.6" fill="var(--lj-pants)" />
      <line x1="15" y1="41" x2="19.4" y2="41" stroke="var(--lj-pants-dk)" strokeWidth=".6" />
      <line x1="20.6" y1="41" x2="25" y2="41" stroke="var(--lj-pants-dk)" strokeWidth=".6" />
      {/* belt */}
      <rect x="13" y="29" width="14" height="2.4" rx="1" fill="var(--lj-boot)" />
      <rect x="18.7" y="29.2" width="2.6" height="2" rx=".4" fill="var(--lj-axe-hi)" />
      {/* plaid shirt */}
      <rect x="13" y="16" width="14" height="15.2" rx="4" fill="var(--lj-shirt)" />
      <g opacity=".55">
        <rect x="16" y="16" width="2.2" height="15.2" fill="var(--lj-shirt-dk)" />
        <rect x="21.5" y="16" width="2.2" height="15.2" fill="var(--lj-shirt-dk)" />
        <rect x="13" y="19.6" width="14" height="1.8" fill="var(--lj-shirt-dk)" />
        <rect x="13" y="25.6" width="14" height="1.8" fill="var(--lj-shirt-dk)" />
      </g>
      <g stroke="var(--lj-plaid)" strokeWidth=".55" opacity=".85">
        <line x1="16.3" y1="16" x2="16.3" y2="31.2" />
        <line x1="20" y1="16" x2="20" y2="31.2" />
        <line x1="23.7" y1="16" x2="23.7" y2="31.2" />
        <line x1="13" y1="20.5" x2="27" y2="20.5" />
        <line x1="13" y1="24.5" x2="27" y2="24.5" />
        <line x1="13" y1="28.5" x2="27" y2="28.5" />
      </g>
      {/* collar */}
      <path d="M15.5 16.4 L18.4 19.2 L17 16" fill="var(--lj-shirt-dk)" />
      <path d="M24.5 16.4 L21.6 19.2 L23 16" fill="var(--lj-shirt-dk)" />
      {/* suspenders */}
      <line x1="15.6" y1="16.2" x2="18" y2="29.3" stroke="var(--lj-plaid)" strokeWidth="1" />
      <line x1="24.4" y1="16.2" x2="22" y2="29.3" stroke="var(--lj-plaid)" strokeWidth="1" />
      {/* resting arm */}
      <path
        d="M13.4 17.5 C11.6 19 11.2 24.5 12 28.5"
        stroke="var(--lj-shirt)"
        strokeWidth="3.1"
        strokeLinecap="round"
        fill="none"
      />
      <path d="M11.6 27.5 L10.6 29.7" stroke="var(--lj-shirt)" strokeWidth="1.6" strokeLinecap="round" />
      <circle cx="10.3" cy="30.2" r="1.35" fill="var(--lj-skin)" />
      {/* head */}
      <ellipse cx="14.6" cy="10.6" rx="1.1" ry="1.4" fill="var(--lj-skin)" />
      <ellipse cx="25.4" cy="10.6" rx="1.1" ry="1.4" fill="var(--lj-skin)" />
      <circle cx="20" cy="10.4" r="5.7" fill="var(--lj-skin)" />
      <path
        d="M14 9.4 C13.3 5.7 16 3.6 20 3.6 C24 3.6 26.7 5.7 26 9.4 C24 7.5 16 7.5 14 9.4 Z"
        fill="var(--lj-hair)"
      />
      <circle cx="17.4" cy="9.9" r="1" fill="#fff" />
      <circle cx="22.6" cy="9.9" r="1" fill="#fff" />
      <circle cx="17.65" cy="10.05" r=".52" fill="#241c28" />
      <circle cx="22.35" cy="10.05" r=".52" fill="#241c28" />
      <path d="M19.5 11.1 Q20 12 19.6 12.6" stroke="var(--lj-skin-sh)" strokeWidth=".5" fill="none" />
      <path
        d="M14.6 9.5 C14 12.7 14.6 16.7 20 17.4 C25.4 16.7 26 12.7 25.4 9.5 C24.4 12.2 22.6 12.9 20 12.9 C17.4 12.9 15.6 12.2 14.6 9.5 Z"
        fill="var(--lj-beard)"
      />
      <path
        d="M14.9 10 C14.9 12.6 16.4 15 20 15.6 C23.6 15 25.1 12.6 25.1 10 C22.9 12 17.1 12 14.9 10 Z"
        fill="var(--lj-beard-dk)"
        opacity=".35"
      />
      <path d="M17.6 11.6 Q20 12.6 22.4 11.6" stroke="var(--lj-beard-dk)" strokeWidth=".55" fill="none" />
      {/* swinging arm + axe */}
      <g className="lj-arm-swing">
        <path
          d="M27.5 17.5 L30.9 23 L30.9 28"
          stroke="var(--lj-shirt)"
          strokeWidth="3.2"
          strokeLinecap="round"
          fill="none"
        />
        <circle cx="30.9" cy="28.6" r="1.45" fill="var(--lj-skin)" />
        <path d="M30.9 28 L32.8 12.6" stroke="var(--lj-handle)" strokeWidth="1.5" strokeLinecap="round" />
        <path
          d="M31.6 24 L32.4 18.5 M31.9 21.5 L32.6 17"
          stroke="var(--lj-handle-dk)"
          strokeWidth=".4"
        />
        {/* curved axe head — a proper flared blade with an eye where the handle passes
            through, not the flat wedge polygon the first pass shipped */}
        <path
          d="M32.2 11.6 C34.3 9.2 37.6 8 39.6 9.3 C40.6 11.7 39 14.2 35.8 16.2
             C34.5 17 33.1 16.6 32.6 15.3 C32.1 13.9 31.9 12.7 32.2 11.6 Z"
          fill="var(--lj-axe)"
        />
        <path
          d="M33 12.4 C34.6 10.6 37 9.7 38.7 10.4"
          stroke="var(--lj-axe-hi)"
          strokeWidth=".6"
          fill="none"
          strokeLinecap="round"
        />
        <circle cx="32.7" cy="13.6" r=".65" fill="var(--lj-handle-dk)" />
      </g>
    </>
  );
}

export function Lumberjack({
  x,
  y,
  height,
  flip = false,
}: {
  /** top-left corner, in the Grove's own coordinate units */
  x: number;
  y: number;
  /** rendered height, in the Grove's own coordinate units; width follows the aspect ratio */
  height: number;
  flip?: boolean;
}) {
  const width = height * ASPECT;
  const svg = (
    <svg x={0} y={0} width={width} height={height} viewBox="0 0 40 56" aria-hidden="true">
      <Body />
    </svg>
  );
  // A slight lean from the ankles toward the trunk — applied before any flip, so on a
  // flipped (mirrored) figure it reads as leaning the other way, i.e. still toward the
  // tree it's standing next to either side of.
  const lean = `rotate(9 ${width / 2} ${height})`;
  if (!flip) {
    return <g transform={`translate(${x} ${y}) ${lean}`}>{svg}</g>;
  }
  return <g transform={`translate(${x + width} ${y}) scale(-1 1) ${lean}`}>{svg}</g>;
}
