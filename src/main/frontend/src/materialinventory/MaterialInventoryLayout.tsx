import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import AppHeader from "../components/AppHeader";
import { useMaterialInventory } from "./MaterialInventoryContext";

function InventoryGroupSwitcher() {
  const { inventoryGroups, currentGroupId, setCurrentInventoryGroup, createInventoryGroup } = useMaterialInventory();
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");

  if (creating) {
    return (
      <form
        className="toolbar"
        onSubmit={async (e) => {
          e.preventDefault();
          if (!name.trim()) return;
          await createInventoryGroup(name.trim());
          setName("");
          setCreating(false);
        }}
      >
        <input
          autoFocus
          aria-label="Inventory group name"
          placeholder="Inventory group name"
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
      {inventoryGroups.length > 0 && (
        <select aria-label="Current inventory group" value={currentGroupId ?? ""} onChange={(e) => setCurrentInventoryGroup(e.target.value)}>
          {inventoryGroups.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </select>
      )}
      <button type="button" onClick={() => setCreating(true)}>
        + New inventory group
      </button>
    </div>
  );
}

export default function MaterialInventoryLayout() {
  const { loading, currentGroupId, inventoryGroups } = useMaterialInventory();

  return (
    <div className="app">
      <AppHeader
        appletIcon="🧶"
        appletName="Material Inventory"
        appletHref="/materialinventory"
        navLinks={
          currentGroupId && (
            <div className="nav-links">
              <NavLink to="/materialinventory" end>
                My inventory
              </NavLink>
              <NavLink to="/materialinventory/requests">Requests</NavLink>
            </div>
          )
        }
        rightSlot={<InventoryGroupSwitcher />}
        appletKey="materialinventory"
        groupId={currentGroupId}
      />

      <div className="container">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : !currentGroupId ? (
          <div className="card">
            <h2>Set up your first inventory group</h2>
            <p className="muted">
              An inventory group is its own separate workspace, kept apart from any Order Tracker business on
              purpose — so tracking yarn never has to share access with everyday order-taking. Use
              "+ New inventory group" above to get started, then link it to a business from that business's
              Manage business page.
            </p>
            {inventoryGroups.length === 0 && <p className="empty">No inventory groups yet.</p>}
          </div>
        ) : (
          <Outlet />
        )}
      </div>

      {currentGroupId && (
        <nav className="tabbar text">
          <NavLink to="/materialinventory" end>
            My inventory
          </NavLink>
          <NavLink to="/materialinventory/requests">Requests</NavLink>
        </nav>
      )}
    </div>
  );
}
