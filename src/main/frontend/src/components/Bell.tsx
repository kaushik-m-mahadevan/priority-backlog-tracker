import { useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { api } from "../api/client";
import { BellIcon } from "./icons";
import type { NeedsAttention } from "../types";

/**
 * Badge counts stale & buried items (design §5). Pending archival requests (§18) will add
 * to this once that feature exists. Clicking opens a Needs Attention popover.
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
    <div className="bell" ref={ref} style={{ display: "inline-flex" }}>
      <button
        className="iconbtn"
        aria-label={`Needs attention: ${total}`}
        onClick={() => setOpen((o) => !o)}
      >
        <BellIcon />
        {total > 0 && <span className="dot" />}
      </button>
      {open && (
        <div className="popover">
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>
            Needs attention {total > 0 && <span className="muted">· {total}</span>}
          </div>
          {total === 0 && <p className="empty">Nothing slipping right now.</p>}
          {stale.length > 0 && (
            <>
              <div className="muted" style={{ fontSize: 11, margin: "6px 0 2px" }}>
                Stale &amp; overdue
              </div>
              {stale.map((f) => (
                <div className="rp-item" key={f.item.id}>
                  {f.item.title}
                  <div className="sub overdue">{f.days}d overdue</div>
                </div>
              ))}
            </>
          )}
          {buried.length > 0 && (
            <>
              <div className="muted" style={{ fontSize: 11, margin: "10px 0 2px" }}>
                Buried low-priority
              </div>
              {buried.map((f) => (
                <div className="rp-item" key={f.item.id}>
                  {f.item.title}
                  <div className="sub">{f.days}d old</div>
                </div>
              ))}
            </>
          )}
        </div>
      )}
    </div>
  );
}
