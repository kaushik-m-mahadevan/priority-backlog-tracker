import { ProductCatalogProvider } from "./ProductCatalogContext";
import ProductCatalogLayout from "./ProductCatalogLayout";

/** Wraps the Product Catalog route subtree with its own group context, kept entirely
 *  separate from Order Tracker's BusinessContext, Finance Tracker's FinanceGroupContext,
 *  and Material Inventory's MaterialInventoryContext (platform integration decision: a
 *  group is scoped to one applet, the switchers must never see each other's groups).
 *  ProductCatalogLayout renders the <Outlet /> for the child routes below it. */
export default function ProductCatalogRoot() {
  return (
    <ProductCatalogProvider>
      <ProductCatalogLayout />
    </ProductCatalogProvider>
  );
}
