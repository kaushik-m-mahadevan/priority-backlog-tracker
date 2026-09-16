import { useProductCatalog } from "../ProductCatalogContext";

/** Scaffold placeholder — the Colorway domain (business-wide catalog CRUD, idea box,
 *  promote-to-catalog flow) lands in the next atomic feature. This page exists so the
 *  applet has a working route and empty state as soon as a group is created. */
export default function ColorwaysPage() {
  const { currentCatalogGroup } = useProductCatalog();

  return (
    <div>
      <h1 className="page-title">Colorways</h1>
      <p className="page-sub">{currentCatalogGroup?.name}</p>
      <div className="card">
        <p className="empty">Product catalog tracking is coming soon.</p>
      </div>
    </div>
  );
}
