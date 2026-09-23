import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useDock } from "../dock/DockContext";
import { useGroups } from "../groups/GroupContext";
import { useKeepAlive } from "../lib/useKeepAlive";
import AppHeader from "./AppHeader";
import Grove from "./Grove";
import { PriorityGlyph, QuickGlyph, AttentionGlyph, TeamGlyph, ItemsGlyph } from "./icons";

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
      <AppHeader
        appletIcon="◆"
        appletName="Backlog Tracker"
        appletHref="/backlog"
        navLinks={
          <div className="nav-links">
            <NavLink to="/backlog/items">Items</NavLink>
          </div>
        }
        extraMenuLinks={[
          { to: "/backlog/groups", label: "Switch group" },
          { to: "/backlog/archive", label: "Completed" },
        ]}
        groupId={currentGroupId}
      />

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
        <NavLink to="/backlog/team" aria-label="Team workload">
          <TeamGlyph size={22} />
        </NavLink>
        <NavLink to="/backlog/items" aria-label="Items">
          <ItemsGlyph size={22} />
        </NavLink>
      </nav>
    </div>
  );
}
