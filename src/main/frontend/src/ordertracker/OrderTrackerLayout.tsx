import { useRef, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import AppHeader from "../components/AppHeader";
import { CustomersGlyph, MoreGlyph, NewOrderGlyph, OrdersGlyph } from "../components/icons";
import ProfileGatePage from "./ProfileGatePage";
import SetupWizardPage from "./pages/SetupWizardPage";
import { useBusiness } from "./BusinessContext";
import { useSetupGate } from "./useSetupGate";
import { useDismissableMenu } from "../lib/useDismissableMenu";
import { usePopoverPosition } from "../lib/usePopoverPosition";

function BusinessSwitcher() {
  const { businesses, currentGroupId, setCurrentBusiness, createBusiness } = useBusiness();
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");

  if (creating) {
    return (
      <form
        className="toolbar"
        onSubmit={async (e) => {
          e.preventDefault();
          if (!name.trim()) return;
          await createBusiness(name.trim());
          setName("");
          setCreating(false);
        }}
      >
        <input
          autoFocus
          aria-label="Business name"
          placeholder="Business name"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button className="primary" type="submit">
          Create
        </button>
        <button type="button" onClick={() => setCreating(false)}>
          Cancel
        </button>
      </form>
    );
  }

  return (
    <div className="toolbar">
      {businesses.length > 0 && (
        <select aria-label="Current business" value={currentGroupId ?? ""} onChange={(e) => setCurrentBusiness(e.target.value)}>
          {businesses.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </select>
      )}
      <button type="button" onClick={() => setCreating(true)}>
        + New business
      </button>
    </div>
  );
}

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
  const { loading, currentGroupId, businesses } = useBusiness();
  const { status: gateStatus, refresh: refreshGate } = useSetupGate(currentGroupId);

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
        rightSlot={<BusinessSwitcher />}
        appletKey="ordertracker"
        groupId={currentGroupId}
      />

      <div className="container">
        {loading || (currentGroupId && gateStatus === "loading") ? (
          <p className="muted">Loading…</p>
        ) : !currentGroupId ? (
          <div className="card">
            <h2>Set up your first business</h2>
            <p className="muted">
              Every business you create here is its own separate Order Tracker workspace — customers, orders,
              and settings never cross between them. Use "+ New business" above to get started.
            </p>
            {businesses.length === 0 && <p className="empty">No businesses yet.</p>}
          </div>
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
