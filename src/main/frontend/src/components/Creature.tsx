/* A small family of friendly creatures for owner avatars.
   Same face; the top feature (ears / tufts / antlers) is what differs.
   Known team members get a distinct creature by their position in the team list;
   anyone else is hashed from their name. */
import { useUsers } from "../users/UsersContext";

const INK = "#40374b";

const PASTELS = [
  "#f4d7c4", // apricot
  "#d6e7cb", // sage
  "#ded6f0", // lilac
  "#c9e6e3", // seafoam
  "#f2d5df", // rose
  "#e8dcc4", // sand
  "#cfe0f0", // sky
  "#ecd9e6", // orchid
  "#dbe8d0", // moss
  "#f0dcc9", // peach
];

/* FNV-1a 32-bit — spreads even near-identical inputs. */
function fnv(s: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

function Face({ scale = 1 }: { scale?: number }) {
  return (
    <>
      <circle cx="12" cy="13.5" r="7.6" fill="#fff" fillOpacity="0.6" />
      <circle cx="12" cy="13.5" r="7.6" fill="none" stroke={INK} strokeWidth={1.6 / scale} />
      <circle cx="9.5" cy="13" r="1.1" fill={INK} />
      <circle cx="14.5" cy="13" r="1.1" fill={INK} />
      <path d="M10 16c1 .9 3 .9 4 0" fill="none" stroke={INK} strokeWidth={1.3 / scale} strokeLinecap="round" />
    </>
  );
}

const S = { fill: "#fff", fillOpacity: 0.6, stroke: INK, strokeWidth: 1.6 };
const L = { fill: "none", stroke: INK, strokeWidth: 1.5, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };

const TOPS: (() => JSX.Element)[] = [
  () => <path d="M5.5 8 L7 3 L10.5 7 M18.5 8 L17 3 L13.5 7" {...S} strokeLinejoin="round" />, // cat
  () => (
    <g {...S}>
      <circle cx="6" cy="6.5" r="2.6" />
      <circle cx="18" cy="6.5" r="2.6" />
    </g>
  ), // bear
  () => (
    <g {...S}>
      <ellipse cx="8.5" cy="5" rx="1.8" ry="4.3" />
      <ellipse cx="15.5" cy="5" rx="1.8" ry="4.3" />
    </g>
  ), // rabbit
  () => <path d="M5 9 L5.5 3 L10.5 7.5 M19 9 L18.5 3 L13.5 7.5" fill="#fff" fillOpacity={0.4} stroke={INK} strokeWidth={1.6} strokeLinejoin="round" />, // fox
  () => <path d="M6.5 7 Q8 2 11 6 M17.5 7 Q16 2 13 6" {...L} />, // owl
  () => (
    <g {...L}>
      <path d="M12 2 L12 6" />
      <path d="M9.5 3.8 L12 2 L14.5 3.8" />
    </g>
  ), // bird
  () => (
    <g {...S}>
      <circle cx="5.5" cy="7" r="3" />
      <circle cx="18.5" cy="7" r="3" />
    </g>
  ), // mouse
  () => (
    <g {...L} strokeWidth={1.3}>
      <path d="M8.5 6 L6.5 2 M8.5 6 L5 4 M8.5 6 L8 2.5" />
      <path d="M15.5 6 L17.5 2 M15.5 6 L19 4 M15.5 6 L16 2.5" />
    </g>
  ), // deer
  () => (
    <g {...S}>
      <circle cx="8" cy="5.5" r="2.3" />
      <circle cx="16" cy="5.5" r="2.3" />
    </g>
  ), // frog
  () => <path d="M8.5 6 Q12 2.5 15.5 6" {...L} />, // penguin
  () => (
    <g {...L} strokeWidth={1.3}>
      <path d="M6.5 7 L4.5 3 M9.5 5.5 L9 1.5 M12 5 L12 1 M14.5 5.5 L15 1.5 M17.5 7 L19.5 3" />
    </g>
  ), // hedgehog
  () => <path d="M9 6 Q7 2 5 3 M15 6 Q17 2 19 3" {...L} strokeWidth={1.3} />, // bee
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
  const idx = useUsers().indexOf(seed);

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
  let topI: number;
  let colI: number;
  if (idx >= 0) {
    topI = idx % TOPS.length;
    colI = (idx * 3 + 1) % PASTELS.length; // spread colour away from creature order
  } else {
    const key = (label || seed).toLowerCase();
    topI = fnv(key) % TOPS.length;
    colI = fnv(key + "~") % PASTELS.length;
  }
  const Top = TOPS[topI];
  const bg = PASTELS[colI];
  const scale = (size * 0.78) / 24;
  return (
    <span className="crt" style={{ width: size, height: size, background: bg }} title={label || seed}>
      <svg width={size * 0.78} height={size * 0.78} viewBox="0 0 24 24" aria-hidden="true">
        <Top />
        <Face scale={scale} />
      </svg>
      {inProgress && <span className="wip" title="in progress" />}
    </span>
  );
}
