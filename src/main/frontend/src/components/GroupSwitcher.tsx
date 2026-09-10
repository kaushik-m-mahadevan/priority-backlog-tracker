import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useGroups } from "../groups/GroupContext";

/** Top-bar control for the active group. */
export default function GroupSwitcher() {
  const { groups, currentGroup, setCurrentGroup } = useGroups();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const nav = useNavigate();

  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div className="navmenu" ref={ref}>
      <button
        className="navmenu-btn groupswitch-btn"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <span className="navmenu-name">{currentGroup?.name ?? "No group"}</span>
        <span className="navmenu-caret" aria-hidden="true">
          ▾
        </span>
      </button>
      {open && (
        <div className="navmenu-pop" role="menu">
          <div className="navmenu-head">Group</div>
          {groups.length === 0 && <div className="muted" style={{ padding: "6px 10px" }}>None yet</div>}
          {groups.map((g) => (
            <button
              key={g.id}
              type="button"
              role="menuitem"
              onClick={() => {
                setCurrentGroup(g.id);
                setOpen(false);
              }}
            >
              {g.id === currentGroup?.id ? "● " : "  "}
              {g.name}
            </button>
          ))}
          <button
            type="button"
            role="menuitem"
            style={{ borderTop: "1px solid var(--border-soft)", marginTop: 4 }}
            onClick={() => {
              setOpen(false);
              nav("/groups");
            }}
          >
            Manage groups…
          </button>
        </div>
      )}
    </div>
  );
}
