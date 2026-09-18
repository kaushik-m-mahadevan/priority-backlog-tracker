import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import AppHeader from "../components/AppHeader";
import { useProductCatalog } from "./ProductCatalogContext";

function CatalogGroupSwitcher() {
  const { catalogGroups, currentGroupId, setCurrentCatalogGroup, createCatalogGroup } = useProductCatalog();
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");

  if (creating) {
    return (
      <form
        className="toolbar"
        onSubmit={async (e) => {
          e.preventDefault();
          if (!name.trim()) return;
          await createCatalogGroup(name.trim());
          setName("");
          setCreating(false);
        }}
      >
        <input
          autoFocus
          aria-label="Catalog group name"
          placeholder="Catalog group name"
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
      {catalogGroups.length > 0 && (
        <select aria-label="Current catalog group" value={currentGroupId ?? ""} onChange={(e) => setCurrentCatalogGroup(e.target.value)}>
          {catalogGroups.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </select>
      )}
      <button type="button" onClick={() => setCreating(true)}>
        + New catalog group
      </button>
    </div>
  );
}

export default function ProductCatalogLayout() {
  const { loading, currentGroupId, catalogGroups } = useProductCatalog();

  return (
    <div className="app">
      <AppHeader
        appletIcon="🎨"
        appletName="Product Catalog"
        appletHref="/productcatalog"
        navLinks={
          currentGroupId && (
            <div className="nav-links">
              <NavLink to="/productcatalog" end>
                Colorways
              </NavLink>
            </div>
          )
        }
        rightSlot={<CatalogGroupSwitcher />}
        appletKey="productcatalog"
        groupId={currentGroupId}
      />

      <div className="container">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : !currentGroupId ? (
          <div className="card">
            <h2>Set up your first catalog group</h2>
            <p className="muted">
              A catalog group is its own separate workspace, kept apart from any Order Tracker business on
              purpose — so managing your product lineup never has to share access with everyday order-taking. Use
              "+ New catalog group" above to get started, then link it to a business from that business's
              Manage business page.
            </p>
            {catalogGroups.length === 0 && <p className="empty">No catalog groups yet.</p>}
          </div>
        ) : (
          <Outlet />
        )}
      </div>

      {currentGroupId && (
        <nav className="tabbar text">
          <NavLink to="/productcatalog" end>
            Colorways
          </NavLink>
        </nav>
      )}
    </div>
  );
}
