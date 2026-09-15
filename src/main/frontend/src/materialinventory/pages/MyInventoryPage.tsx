import { useMaterialInventory } from "../MaterialInventoryContext";

/** Scaffold placeholder — per-business YarnType canonical entity and personal inventory
 *  CRUD land in the next atomic feature. This page exists so the applet has a working
 *  route and empty state as soon as a group is created. */
export default function MyInventoryPage() {
  const { currentInventoryGroup } = useMaterialInventory();

  return (
    <div>
      <h1 className="page-title">My inventory</h1>
      <p className="page-sub">{currentInventoryGroup?.name}</p>
      <div className="card">
        <p className="empty">Yarn inventory tracking is coming soon.</p>
      </div>
    </div>
  );
}
