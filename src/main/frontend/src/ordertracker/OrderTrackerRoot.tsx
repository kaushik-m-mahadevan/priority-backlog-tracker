import { BusinessProvider } from "./BusinessContext";
import OrderTrackerLayout from "./OrderTrackerLayout";

/** Wraps the Order Tracker route subtree with its own business (group) context, kept
 *  entirely separate from Backlog Tracker's GroupContext (platform integration decision:
 *  a group is scoped to one applet, the two switchers must never see each other's groups).
 *  OrderTrackerLayout itself renders the <Outlet /> for the child routes below it. */
export default function OrderTrackerRoot() {
  return (
    <BusinessProvider>
      <OrderTrackerLayout />
    </BusinessProvider>
  );
}
