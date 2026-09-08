import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useDock } from "../dock/DockContext";
import Bell from "./Bell";
import {
  GearIcon,
  SidebarIcon,
  PriorityGlyph,
  QuickGlyph,
  AttentionGlyph,
  TeamGlyph,
  ItemsGlyph,
} from "./icons";

export default function Layout() {
  const { user, logout } = useAuth();
  const { shown, setShown } = useDock();
  const onDashboard = useLocation().pathname === "/";

  return (
    <div className="app">
      <nav className="nav">
        {onDashboard && (
          <button
            className={`iconbtn dock-toggle${shown ? " on" : ""}`}
            title={shown ? "Hide panels" : "Show panels"}
            aria-label={shown ? "Hide panels" : "Show panels"}
            onClick={() => setShown(!shown)}
          >
            <SidebarIcon />
          </button>
        )}
        <Link to="/" className="brand">
          ◆ Backlog Tracker
        </Link>
        <NavLink to="/items">Items</NavLink>
        <NavLink to="/archive">Completed</NavLink>
        <span className="spacer" />
        <Bell />
        <Link to="/settings" className="iconbtn" aria-label="Settings" title="Settings">
          <GearIcon />
        </Link>
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
