import { MaterialInventoryProvider } from "./MaterialInventoryContext";
import MaterialInventoryLayout from "./MaterialInventoryLayout";

/** Wraps the Material Inventory route subtree with its own group context, kept entirely
 *  separate from Order Tracker's BusinessContext, Finance Tracker's FinanceGroupContext,
 *  and Backlog Tracker's GroupContext (platform integration decision: a group is scoped
 *  to one applet, the switchers must never see each other's groups).
 *  MaterialInventoryLayout renders the <Outlet /> for the child routes below it. */
export default function MaterialInventoryRoot() {
  return (
    <MaterialInventoryProvider>
      <MaterialInventoryLayout />
    </MaterialInventoryProvider>
  );
}
