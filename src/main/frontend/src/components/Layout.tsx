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
const GROVE_ROUTES = [
  "/backlog/quick-wins",
  "/backlog/attention",
  "/backlog/team",
  "/backlog/items",
  "/backlog/archive",
];

export default function Layout() {
  const { shown, setShown } = useDock();
  const pathname = useLocation().pathname;
  const onDashboard = pathname === "/backlog";
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
        {/* Goes to the applet launcher, not the Backlog Tracker dashboard — this is also
            rendered on /settings, which isn't under /backlog (Phase 4 reshapes that). */}
        <Link to="/" className="brand">
          ◆ Backlog Tracker
        </Link>
        <div className="nav-links">
          <NavLink to="/backlog/items">Items</NavLink>
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
        <NavLink to="/backlog" end aria-label="Priority">
          <PriorityGlyph size={22} />
        </NavLink>
        <NavLink to="/backlog/quick-wins" aria-label="Quick wins">
          <QuickGlyph size={22} />
        </NavLink>
        <NavLink to="/backlog/attention" aria-label="Needs attention">
          <AttentionGlyph size={22} />
        </NavLink>
        <NavLink to="/backlog/groups" aria-label="Groups">
          <TeamGlyph size={22} />
        </NavLink>
        <NavLink to="/backlog/items" aria-label="Items">
          <ItemsGlyph size={22} />
        </NavLink>
      </nav>
    </div>
  );
}
