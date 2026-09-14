import { useState } from "react";
import { Link, NavLink, Outlet } from "react-router-dom";
import { useBusiness } from "./BusinessContext";

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
        <select value={currentGroupId ?? ""} onChange={(e) => setCurrentBusiness(e.target.value)}>
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

export default function OrderTrackerLayout() {
  const { loading, currentGroupId, businesses } = useBusiness();

  return (
    <div className="app">
      <nav className="nav">
        <Link to="/" className="brand">
          ✂ Order Tracker
        </Link>
        {currentGroupId && (
          <div className="nav-links">
            <NavLink to="/ordertracker/orders">Orders</NavLink>
            <NavLink to="/ordertracker/bulk-orders">Bulk Orders</NavLink>
            <NavLink to="/ordertracker/customers">Customers</NavLink>
            <NavLink to="/ordertracker/business-settings">Business</NavLink>
          </div>
        )}
        <span className="spacer" />
        <BusinessSwitcher />
      </nav>

      <div className="container">
        {loading ? (
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
        ) : (
          <Outlet />
        )}
      </div>

      {/* .nav-links (the desktop nav) hides below 760px, same as Backlog Tracker's own
          Layout — this is Order Tracker's equivalent bottom bar so Business/Customers
          stay reachable on mobile instead of just disappearing. */}
      {currentGroupId && (
        <nav className="tabbar text">
          <NavLink to="/ordertracker/orders">Orders</NavLink>
          <NavLink to="/ordertracker/bulk-orders">Bulk</NavLink>
          <NavLink to="/ordertracker/customers">Customers</NavLink>
          <NavLink to="/ordertracker/business-settings">Business</NavLink>
        </nav>
      )}
    </div>
  );
}
