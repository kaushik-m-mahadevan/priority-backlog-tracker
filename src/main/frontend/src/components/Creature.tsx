/* Owner avatars — a colourful cartoon animal on a soft pastel disc.
   Known team members get a stable animal by their slot in the team list;
   anyone else is hashed from their name. */
import { useUsers } from "../users/UsersContext";

const ANIMALS = [
  "\u{1F98A}", // fox
  "\u{1F43F}\u{FE0F}", // squirrel
  "\u{1F989}", // owl
  "\u{1F43B}", // bear
  "\u{1F428}", // koala
  "\u{1F43C}", // panda
  "\u{1F430}", // rabbit
  "\u{1F438}", // frog
  "\u{1F437}", // pig
  "\u{1F42E}", // cow
  "\u{1F426}", // bird
  "\u{1F986}", // duck
  "\u{1F422}", // turtle
  "\u{1F994}", // hedgehog
  "\u{1F419}", // octopus
  "\u{1F41D}", // bee
  "\u{1F431}", // cat
  "\u{1F436}", // dog
  "\u{1F42D}", // mouse
  "\u{1F984}", // unicorn
];

const PASTELS = [
  "#f4d7c4",
  "#d6e7cb",
  "#ded6f0",
  "#c9e6e3",
  "#f2d5df",
  "#e8dcc4",
  "#cfe0f0",
  "#ecd9e6",
  "#dbe8d0",
  "#f0dcc9",
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
      <span
        className="crt dashed"
        style={{ width: size, height: size }}
        title={label || "Unassigned"}
      >
        <span className="crt-dot" />
        {inProgress && <span className="wip" />}
      </span>
    );
  }

  let animalI: number;
  let colI: number;
  if (idx >= 0) {
    animalI = idx % ANIMALS.length;
    colI = (idx * 3 + 1) % PASTELS.length;
  } else {
    const key = (label || seed).toLowerCase();
    animalI = fnv(key) % ANIMALS.length;
    colI = fnv(key + "~") % PASTELS.length;
  }

  return (
    <span
      className="crt"
      style={{ width: size, height: size, background: PASTELS[colI], fontSize: Math.round(size * 0.6) }}
      title={label || seed}
    >
      <span aria-hidden="true">{ANIMALS[animalI]}</span>
      {inProgress && <span className="wip" title="in progress" />}
    </span>
  );
}
