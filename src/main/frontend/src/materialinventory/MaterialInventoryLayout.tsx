import { NavLink, Outlet } from "react-router-dom";
import AppHeader from "../components/AppHeader";
import InventoryGroupsPage from "./pages/InventoryGroupsPage";
import { useMaterialInventory } from "./MaterialInventoryContext";

export default function MaterialInventoryLayout() {
  const { loading, currentGroupId } = useMaterialInventory();

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
              <NavLink to="/materialinventory/assignments">Assignments</NavLink>
              <NavLink to="/materialinventory/manage">Manage</NavLink>
            </div>
          )
        }
        extraMenuLinks={[{ to: "/materialinventory/groups", label: "Switch inventory group" }]}
        groupId={currentGroupId}
      />

      <div className="container">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : !currentGroupId ? (
          <InventoryGroupsPage />
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
          <NavLink to="/materialinventory/assignments">Assignments</NavLink>
          <NavLink to="/materialinventory/manage">Manage</NavLink>
        </nav>
      )}
    </div>
  );
}
