import { useRef } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import AppHeader from "../components/AppHeader";
import { CustomersGlyph, MoreGlyph, NewOrderGlyph, OrdersGlyph } from "../components/icons";
import BusinessesPage from "./pages/BusinessesPage";
import ProfileGatePage from "./ProfileGatePage";
import SetupWizardPage from "./pages/SetupWizardPage";
import { useBusiness } from "./BusinessContext";
import { useSetupGate } from "./useSetupGate";
import { useDismissableMenu } from "../lib/useDismissableMenu";
import { usePopoverPosition } from "../lib/usePopoverPosition";

/** Overflow tab for the mobile bottom bar (ui-3: 4 icons max — Orders / New Order /
 *  Customers / More — so My Work / Team / Business live behind one "more" popover
 *  instead of stretching the tabbar past a comfortable tap target count). */
function MoreTab() {
  const { open, setOpen, ref } = useDismissableMenu<HTMLDivElement>();
  const triggerRef = useRef<HTMLButtonElement>(null);
  const { popoverRef, style: popoverStyle } = usePopoverPosition(triggerRef, open);

  return (
    <div ref={ref} style={{ position: "relative", display: "flex" }}>
      <button
        type="button"
        ref={triggerRef}
        className={open ? "active" : ""}
        aria-label="More"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <MoreGlyph size={22} />
      </button>
      {open && (
        <div className="navmenu-pop" role="menu" ref={popoverRef} style={popoverStyle}>
          <NavLink to="/ordertracker/my-work" role="menuitem" onClick={() => setOpen(false)}>
            My Work
          </NavLink>
          <NavLink to="/ordertracker/manage-business" role="menuitem" onClick={() => setOpen(false)}>
            Team
          </NavLink>
          <NavLink to="/ordertracker/business-settings" role="menuitem" onClick={() => setOpen(false)}>
            Business
          </NavLink>
        </div>
      )}
    </div>
  );
}

export default function OrderTrackerLayout() {
  const { loading, currentBusiness, currentGroupId } = useBusiness();
  const { status: gateStatus, refresh: refreshGate } = useSetupGate(currentGroupId);
  // The chooser must stay reachable even while the current business is mid-wizard or
  // mid-profile-gate — otherwise creating a new business (which selects it immediately)
  // traps the user in its wizard with no way back to "Switch business" in the account
  // menu, since that menu is otherwise the only escape hatch now that the switcher no
  // longer lives in the header (ui-1).
  const onBusinessesPage = useLocation().pathname === "/ordertracker/businesses";

  // Fully blocking (design decision): while a business is mid-wizard or the caller has no
  // profile yet in an already-set-up business, nav links and the bottom tabbar disappear
  // along with the Outlet — there is nothing else reachable until the gate clears.
  const showNav = !!currentGroupId && gateStatus === "ready";

  return (
    <div className="app">
      <AppHeader
        appletIcon="✂"
        appletName="Order Tracker"
        appletHref="/ordertracker/orders"
        navLinks={
          showNav && (
            <div className="nav-links">
              <NavLink to="/ordertracker/orders">Orders</NavLink>
              <NavLink to="/ordertracker/my-work">My Work</NavLink>
              <NavLink to="/ordertracker/customers">Customers</NavLink>
              <NavLink to="/ordertracker/manage-business">Manage business</NavLink>
              <NavLink to="/ordertracker/business-settings">Business</NavLink>
            </div>
          )
        }
        extraMenuLinks={[{ to: "/ordertracker/businesses", label: "Switch business" }]}
        groupId={currentGroupId}
        groupName={currentBusiness?.name}
      />

      <div className="container">
        {loading || (currentGroupId && gateStatus === "loading" && !onBusinessesPage) ? (
          <p className="muted">Loading…</p>
        ) : !currentGroupId || onBusinessesPage ? (
          <BusinessesPage />
        ) : gateStatus === "wizard" ? (
          <SetupWizardPage onDone={refreshGate} />
        ) : gateStatus === "profile-gate" ? (
          <ProfileGatePage onDone={refreshGate} />
        ) : (
          <Outlet />
        )}
      </div>

      {/* .nav-links (the desktop nav) hides below 760px, same as Backlog Tracker's own
          Layout — this is Order Tracker's equivalent bottom bar so Business/Customers
          stay reachable on mobile instead of just disappearing. Icon-only (ui-3), matching
          Backlog Tracker's own tabbar convention — My Work/Team/Business move behind More. */}
      {showNav && (
        <nav className="tabbar">
          <NavLink to="/ordertracker/orders" end aria-label="Orders">
            <OrdersGlyph size={22} />
          </NavLink>
          <NavLink to="/ordertracker/orders/new" aria-label="New order">
            <NewOrderGlyph size={22} />
          </NavLink>
          <NavLink to="/ordertracker/customers" aria-label="Customers">
            <CustomersGlyph size={22} />
          </NavLink>
          <MoreTab />
        </nav>
      )}
    </div>
  );
}
