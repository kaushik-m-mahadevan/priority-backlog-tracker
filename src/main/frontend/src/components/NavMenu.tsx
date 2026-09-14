import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

/** Avatar button in the top bar; dropdown holds the secondary nav + sign out. */
export default function NavMenu() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

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

  const name = user?.name ?? "";
  const initial = name.trim().slice(0, 1).toUpperCase() || "?";

  return (
    <div className="navmenu" ref={ref}>
      <button
        className="navmenu-btn"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account menu"
      >
        <span className="navmenu-avatar" aria-hidden="true">
          {initial}
        </span>
        <span className="navmenu-name">{name}</span>
        <span className="navmenu-caret" aria-hidden="true">
          ▾
        </span>
      </button>
      {open && (
        <div className="navmenu-pop" role="menu">
          <div className="navmenu-head">
            {name}
            {user?.handle && <span className="muted"> · @{user.handle}</span>}
          </div>
          <Link to="/backlog/archive" role="menuitem" onClick={() => setOpen(false)}>
            Completed
          </Link>
          {/* Settings and Admin console are platform-level, not Backlog-Tracker-specific —
              reached from the launcher's own cards, not shortcut here (design: platform
              integration follow-up, keeps every applet's own nav scoped to that applet). */}
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              logout();
            }}
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}
