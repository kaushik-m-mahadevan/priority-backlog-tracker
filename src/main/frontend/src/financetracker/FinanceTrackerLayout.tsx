import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import AppHeader from "../components/AppHeader";
import { useFinanceGroup } from "./FinanceGroupContext";

function FinanceGroupSwitcher() {
  const { financeGroups, currentGroupId, setCurrentFinanceGroup, createFinanceGroup } = useFinanceGroup();
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");

  if (creating) {
    return (
      <form
        className="toolbar"
        onSubmit={async (e) => {
          e.preventDefault();
          if (!name.trim()) return;
          await createFinanceGroup(name.trim());
          setName("");
          setCreating(false);
        }}
      >
        <input
          autoFocus
          aria-label="Finance group name"
          placeholder="Finance group name"
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
      {financeGroups.length > 0 && (
        <select aria-label="Current finance group" value={currentGroupId ?? ""} onChange={(e) => setCurrentFinanceGroup(e.target.value)}>
          {financeGroups.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </select>
      )}
      <button type="button" onClick={() => setCreating(true)}>
        + New finance group
      </button>
    </div>
  );
}

export default function FinanceTrackerLayout() {
  const { loading, currentGroupId, financeGroups } = useFinanceGroup();

  return (
    <div className="app">
      <AppHeader
        appletIcon="💰"
        appletName="Finance Tracker"
        appletHref="/financetracker"
        navLinks={
          currentGroupId && (
            <div className="nav-links">
              <NavLink to="/financetracker">Overview</NavLink>
            </div>
          )
        }
        rightSlot={<FinanceGroupSwitcher />}
      />

      <div className="container">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : !currentGroupId ? (
          <div className="card">
            <h2>Set up your first finance group</h2>
            <p className="muted">
              A finance group is its own separate workspace, kept apart from any Order Tracker business on
              purpose — so tracking payments never has to share access with everyday order-taking. Use
              "+ New finance group" above to get started, then link it to a business from that business's
              Manage business page.
            </p>
            {financeGroups.length === 0 && <p className="empty">No finance groups yet.</p>}
          </div>
        ) : (
          <Outlet />
        )}
      </div>

      {currentGroupId && (
        <nav className="tabbar text">
          <NavLink to="/financetracker" end>
            Overview
          </NavLink>
        </nav>
      )}
    </div>
  );
}
