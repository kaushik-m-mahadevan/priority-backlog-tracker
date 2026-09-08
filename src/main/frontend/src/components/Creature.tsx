/* A small family of friendly creatures for owner avatars.
   Same face; the top feature (ears / tufts / beak) is what differs. */

const INK = "#40374b";

const PASTELS = [
  "#f3d9c6", // apricot
  "#d8e6cf", // sage
  "#dcd7ef", // lilac
  "#cfe6e4", // seafoam
  "#f1d7df", // rose
  "#e6ddc9", // sand
  "#d2e0ef", // sky
  "#e9dcef", // orchid
];

function hash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}

function Face() {
  return (
    <>
      <circle cx="12" cy="13" r="7.5" fill="#fff" fillOpacity="0.55" />
      <circle cx="12" cy="13" r="7.5" fill="none" stroke={INK} strokeWidth="1.4" />
      <circle cx="9.6" cy="12.4" r="1" fill={INK} />
      <circle cx="14.4" cy="12.4" r="1" fill={INK} />
      <path d="M10.3 15.4c.9.7 2.5.7 3.4 0" fill="none" stroke={INK} strokeWidth="1.2" strokeLinecap="round" />
    </>
  );
}

const TOPS: ((k: string) => JSX.Element)[] = [
  // cat
  (k) => <path key={k} d="M6 8 L7.5 4 L10 7 M18 8 L16.5 4 L14 7" fill="none" stroke={INK} strokeWidth="1.4" strokeLinejoin="round" />,
  // bear
  (k) => (
    <g key={k} fill="#fff" fillOpacity="0.55" stroke={INK} strokeWidth="1.4">
      <circle cx="6.5" cy="7" r="2.2" />
      <circle cx="17.5" cy="7" r="2.2" />
    </g>
  ),
  // rabbit
  (k) => (
    <g key={k} fill="#fff" fillOpacity="0.55" stroke={INK} strokeWidth="1.4">
      <ellipse cx="9" cy="5.5" rx="1.6" ry="4" />
      <ellipse cx="15" cy="5.5" rx="1.6" ry="4" />
    </g>
  ),
  // fox
  (k) => <path key={k} d="M6 9 L6.5 4 L10.5 7.5 M18 9 L17.5 4 L13.5 7.5" fill="#fff" fillOpacity="0.4" stroke={INK} strokeWidth="1.4" strokeLinejoin="round" />,
  // owl
  (k) => <path key={k} d="M7 7 Q8 3 10.5 6 M17 7 Q16 3 13.5 6" fill="none" stroke={INK} strokeWidth="1.4" strokeLinecap="round" />,
  // bird (tuft + beak)
  (k) => (
    <g key={k} fill="none" stroke={INK} strokeWidth="1.4" strokeLinecap="round">
      <path d="M12 3 L12 6.5" />
      <path d="M10.5 4.5 L12 3 L13.5 4.5" fill="none" />
    </g>
  ),
  // mouse
  (k) => (
    <g key={k} fill="#fff" fillOpacity="0.55" stroke={INK} strokeWidth="1.4">
      <circle cx="6" cy="7.5" r="2.6" />
      <circle cx="18" cy="7.5" r="2.6" />
    </g>
  ),
  // deer (antlers)
  (k) => (
    <g key={k} fill="none" stroke={INK} strokeWidth="1.3" strokeLinecap="round">
      <path d="M9 6 L7.5 2.5 M9 6 L6 4.5 M9 6 L8.5 3" />
      <path d="M15 6 L16.5 2.5 M15 6 L18 4.5 M15 6 L15.5 3" />
    </g>
  ),
  // frog (top eyes)
  (k) => (
    <g key={k} fill="#fff" fillOpacity="0.55" stroke={INK} strokeWidth="1.4">
      <circle cx="8.5" cy="6" r="2" />
      <circle cx="15.5" cy="6" r="2" />
    </g>
  ),
  // penguin (plain, small crown)
  (k) => <path key={k} d="M9 6 Q12 3.5 15 6" fill="none" stroke={INK} strokeWidth="1.4" strokeLinecap="round" />,
  // hedgehog (spikes)
  (k) => (
    <g key={k} fill="none" stroke={INK} strokeWidth="1.2" strokeLinecap="round">
      <path d="M7 7 L5.5 4 M10 5.5 L9.5 2.5 M12 5 L12 2 M14 5.5 L14.5 2.5 M17 7 L18.5 4" />
    </g>
  ),
  // bee (antennae)
  (k) => (
    <g key={k} fill="none" stroke={INK} strokeWidth="1.3" strokeLinecap="round">
      <path d="M9.5 6 Q8 3 6.5 3.5 M14.5 6 Q16 3 17.5 3.5" />
    </g>
  ),
];

export function Creature({
  seed,
  label,
  inProgress,
  size = 28,
}: {
  seed: string | null;
  label?: string;
  inProgress?: boolean;
  size?: number;
}) {
  if (!seed) {
    return (
      <span className="crt dashed" style={{ width: size, height: size }} title={label || "Unassigned"}>
        <svg width={size * 0.5} height={size * 0.5} viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="2.4" fill={INK} />
        </svg>
        {inProgress && <span className="wip" />}
      </span>
    );
  }
  const h = hash(seed);
  const top = TOPS[h % TOPS.length];
  const bg = PASTELS[Math.floor(h / TOPS.length) % PASTELS.length];
  return (
    <span className="crt" style={{ width: size, height: size, background: bg }} title={label || seed}>
      <svg width={size * 0.72} height={size * 0.72} viewBox="0 0 24 24" aria-hidden="true">
        {top("t")}
        <Face />
      </svg>
      {inProgress && <span className="wip" title="in progress" />}
    </span>
  );
}
