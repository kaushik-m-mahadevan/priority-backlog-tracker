import { NavLink, Outlet } from "react-router-dom";
import AppHeader from "../components/AppHeader";
import CatalogGroupsPage from "./pages/CatalogGroupsPage";
import { useProductCatalog } from "./ProductCatalogContext";

export default function ProductCatalogLayout() {
  const { loading, currentGroupId } = useProductCatalog();

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
        extraMenuLinks={[{ to: "/productcatalog/groups", label: "Switch catalog group" }]}
        groupId={currentGroupId}
      />

      <div className="container">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : !currentGroupId ? (
          <CatalogGroupsPage />
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
