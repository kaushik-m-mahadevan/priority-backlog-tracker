import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useDismissableMenu } from "../lib/useDismissableMenu";

export interface NavMenuLink {
  to: string;
  label: string;
}

/** Avatar button in the top bar; dropdown holds any applet-specific quick links (e.g.
 *  Backlog Tracker's "Completed") plus account info and sign out. */
export default function NavMenu({ extraLinks }: { extraLinks?: NavMenuLink[] }) {
  const { user, logout } = useAuth();
  const { open, setOpen, ref } = useDismissableMenu<HTMLDivElement>();

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
          {extraLinks?.map((link) => (
            <Link key={link.to} to={link.to} role="menuitem" onClick={() => setOpen(false)}>
              {link.label}
            </Link>
          ))}
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
