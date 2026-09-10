import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useDock } from "../dock/DockContext";
import { useKeepAlive } from "../lib/useKeepAlive";
import Bell from "./Bell";
import NavMenu from "./NavMenu";
import GroupSwitcher from "./GroupSwitcher";
import {
  SidebarIcon,
  PriorityGlyph,
  QuickGlyph,
  AttentionGlyph,
  TeamGlyph,
  ItemsGlyph,
} from "./icons";

export default function Layout() {
  const { shown, setShown } = useDock();
  const onDashboard = useLocation().pathname === "/";
  useKeepAlive();

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
        <div className="nav-links">
          <NavLink to="/items">Items</NavLink>
        </div>
        <span className="spacer" />
        <GroupSwitcher />
        <Bell />
        <NavMenu />
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
        <NavLink to="/groups" aria-label="Groups">
          <TeamGlyph size={22} />
        </NavLink>
        <NavLink to="/items" aria-label="Items">
          <ItemsGlyph size={22} />
        </NavLink>
      </nav>
    </div>
  );
}
