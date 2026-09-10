import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useDock } from "../dock/DockContext";
import { useKeepAlive } from "../lib/useKeepAlive";
import Bell from "./Bell";
import NavMenu from "./NavMenu";
import GroupSwitcher from "./GroupSwitcher";
import Grove from "./Grove";
import {
  SidebarIcon,
  PriorityGlyph,
  QuickGlyph,
  AttentionGlyph,
  TeamGlyph,
  ItemsGlyph,
} from "./icons";

/** Group-scoped pages that carry the Grove as a first-class visual (the dashboard
 *  renders its own). Settings / Admin / Manage-groups deliberately don't. */
const GROVE_ROUTES = ["/quick-wins", "/attention", "/team", "/items", "/archive"];

export default function Layout() {
  const { shown, setShown } = useDock();
  const pathname = useLocation().pathname;
  const onDashboard = pathname === "/";
  const withGrove = GROVE_ROUTES.includes(pathname);
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

      <div className={`container${withGrove ? " has-grove" : ""}`}>
        {withGrove ? (
          <>
            <div className="cg-main">
              <Outlet />
            </div>
            <aside className="cg-grove">
              <Grove solo />
            </aside>
          </>
        ) : (
          <Outlet />
        )}
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
