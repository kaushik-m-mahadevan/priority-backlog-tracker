import { FinanceGroupProvider } from "./FinanceGroupContext";
import FinanceTrackerLayout from "./FinanceTrackerLayout";

/** Wraps the Finance Tracker route subtree with its own group context, kept entirely
 *  separate from Order Tracker's BusinessContext and Backlog Tracker's GroupContext
 *  (platform integration decision: a group is scoped to one applet, the switchers must
 *  never see each other's groups). FinanceTrackerLayout renders the <Outlet /> for the
 *  child routes below it. */
export default function FinanceTrackerRoot() {
  return (
    <FinanceGroupProvider>
      <FinanceTrackerLayout />
    </FinanceGroupProvider>
  );
}
