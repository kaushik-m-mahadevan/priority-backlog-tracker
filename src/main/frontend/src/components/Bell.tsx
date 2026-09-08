import { useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { api } from "../api/client";
import { BellIcon, AgeIcon, OverdueIcon } from "./icons";
import { ageShort } from "../lib/format";
import type { NeedsAttention } from "../types";

/**
 * Count = stale & buried items (design §5); pending archival requests (§18) will add to
 * it once that exists. Number sits in a bubble to the left of the bell.
 */
export default function Bell() {
  const [data, setData] = useState<NeedsAttention | null>(null);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const loc = useLocation();

  useEffect(() => {
    api
      .get<NeedsAttention>("/insights/needs-attention")
      .then(setData)
      .catch(() => setData(null));
  }, [loc.pathname]);

  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);

  const stale = data?.staleAndOverdue ?? [];
  const buried = data?.buriedLowPriority ?? [];
  const total = stale.length + buried.length;

  return (
    <div className="bell" ref={ref}>
      {total > 0 && <span className="bub">{total}</span>}
      <button className="iconbtn" aria-label={`Needs attention: ${total}`} onClick={() => setOpen((o) => !o)}>
        <BellIcon />
      </button>
      {open && (
        <div className="popover">
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>Needs attention</div>
          {total === 0 && <p className="empty">Nothing slipping. Nice and calm.</p>}
          {stale.map((f) => (
            <div className="rp-item" key={f.item.id}>
              {f.item.title}
              <div className="sub hot">
                <OverdueIcon /> {ageShort(f.days)} past due
              </div>
            </div>
          ))}
          {buried.map((f) => (
            <div className="rp-item" key={f.item.id}>
              {f.item.title}
              <div className="sub">
                <AgeIcon /> {ageShort(f.days)} untouched
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
