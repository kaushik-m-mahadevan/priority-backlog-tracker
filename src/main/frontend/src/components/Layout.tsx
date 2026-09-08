import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useTheme } from "../theme/ThemeContext";
import Bell from "./Bell";
import {
  GearIcon,
  PriorityGlyph,
  QuickGlyph,
  AttentionGlyph,
  TeamGlyph,
  ItemsGlyph,
} from "./icons";

function GearMenu() {
  const nav = useNavigate();
  const { theme, setTheme } = useTheme();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);
  return (
    <div ref={ref} style={{ position: "relative", display: "inline-flex" }}>
      <button className="iconbtn" aria-label="Settings and theme" onClick={() => setOpen((o) => !o)}>
        <GearIcon />
      </button>
      {open && (
        <div className="menu">
          <button
            onClick={() => {
              setOpen(false);
              nav("/settings");
            }}
          >
            Settings
          </button>
          <div className="seg">
            <button className={theme === "dusk" ? "on" : ""} onClick={() => setTheme("dusk")}>
              Dusk
            </button>
            <button className={theme === "tide" ? "on" : ""} onClick={() => setTheme("tide")}>
              Tide
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default function Layout() {
  const { user, logout } = useAuth();
  return (
    <div className="app">
      <nav className="nav">
        <Link to="/" className="brand">
          ◆ Backlog Tracker
        </Link>
        <NavLink to="/items">Items</NavLink>
        <NavLink to="/archive">Completed</NavLink>
        <span className="spacer" />
        <Bell />
        <GearMenu />
        {user && <span className="who">{user.name}</span>}
        <button className="ghost" onClick={logout}>
          Sign out
        </button>
      </nav>

      <div className="container">
        <Outlet />
      </div>

      <nav className="tabbar">
        <NavLink to="/" end aria-label="Priority">
          <PriorityGlyph size={22} />
        </NavLink>
        <NavLink to="/quick-wins" aria-label="Quick wins">
          <QuickGlyph size={22} />
        </NavLink>
        <NavLink to="/attention" aria-label="Needs attention">
          <AttentionGlyph size={22} />
        </NavLink>
        <NavLink to="/team" aria-label="Team workload">
          <TeamGlyph size={22} />
        </NavLink>
        <NavLink to="/items" aria-label="Items">
          <ItemsGlyph size={22} />
        </NavLink>
      </nav>
    </div>
  );
}
