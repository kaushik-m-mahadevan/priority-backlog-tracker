import { ReactNode, useState } from "react";
import { ChevronIcon, PinIcon } from "./icons";

interface Props {
  id: string;
  title: string;
  count?: number;
  children: ReactNode;
}

function readPin(id: string): boolean {
  try {
    return sessionStorage.getItem(`pbt.pin.${id}`) === "1";
  } catch {
    return false;
  }
}
function writePin(id: string, on: boolean) {
  try {
    sessionStorage.setItem(`pbt.pin.${id}`, on ? "1" : "0");
  } catch {
    /* ignore */
  }
}

/** Collapsed by default; click to peek; pin to keep open for the session. */
export default function RailPanel({ id, title, count, children }: Props) {
  const [pinned, setPinned] = useState(() => readPin(id));
  const [open, setOpen] = useState(pinned);

  const expanded = open || pinned;

  return (
    <section className="rail-panel">
      <header onClick={() => setOpen((o) => !o)}>
        <ChevronIcon open={expanded} />
        <span className="rp-title">{title}</span>
        {count !== undefined && <span className="rp-count">{count}</span>}
        <button
          className={`iconbtn${pinned ? " on" : ""}`}
          title={pinned ? "Unpin" : "Pin for this session"}
          aria-label={pinned ? "Unpin panel" : "Pin panel"}
          onClick={(e) => {
            e.stopPropagation();
            const next = !pinned;
            setPinned(next);
            writePin(id, next);
            if (next) setOpen(true);
          }}
        >
          <PinIcon />
        </button>
      </header>
      {expanded && <div className="rp-body">{children}</div>}
    </section>
  );
}
