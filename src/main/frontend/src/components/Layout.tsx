import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useDock } from "../dock/DockContext";
import { useGroups } from "../groups/GroupContext";
import { useKeepAlive } from "../lib/useKeepAlive";
import Bell from "./Bell";
import NavMenu from "./NavMenu";
import GroupSwitcher from "./GroupSwitcher";
import Grove from "./Grove";
import {
  HomeIcon,
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
  const { currentGroupId } = useGroups();
  const pathname = useLocation().pathname;
  const onDashboard = pathname === "/backlog";
  const withGrove = GROVE_ROUTES.includes(pathname);
  useKeepAlive();

  return (
    <div className="app">
      <nav className="nav">
        {/* Distinct from the brand link below: this always goes to the applet launcher,
            the brand always stays inside Backlog Tracker (design: platform integration
            follow-up — clicking an applet's own name should never bounce you out of it). */}
        <Link to="/" className="iconbtn" title="Back to console" aria-label="Back to console">
          <HomeIcon />
        </Link>
        <Link to="/backlog" className="brand">
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

      {/* Only the "show panels" affordance floats off the edge — nothing to show when
          there's no group yet, and the "hide" control lives inline in the panel's own
          header once it's open (LeftDock), not as a second floating button. */}
      {onDashboard && currentGroupId && !shown && (
        <button className="dock-edge-tab" title="Show panels" aria-label="Show panels" onClick={() => setShown(true)}>
          ›
        </button>
      )}

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
