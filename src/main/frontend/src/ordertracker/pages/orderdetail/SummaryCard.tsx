import { orderTrackerApi } from "../../api";
import { TimeStageControl } from "../../TimeTracking";
import { creatorName } from "./creatorName";
import type { LinkableNeedleType, LinkableYarnType } from "../../OrderFormFields";
import type { Creator, Customer, OrderView } from "../../types";

/** Extracted from OrderDetailPage.tsx (fdup-1) — fully self-contained via props, no
 *  closure over the parent's state. The "What you need" card's own-inventory shortfall
 *  check is opt-in (only for mandatory items with a linkedYarnTypeId/linkedNeedleTypeId)
 *  and only ever compares against the viewer's own on-hand count (design decision), never
 *  a cross-member total. */
export function SummaryCard({
  groupId,
  order,
  customers,
  creators,
  linkedYarnTypes,
  linkedNeedleTypes,
  myYarnInventory,
  myNeedleInventory,
  onUpdated,
}: {
  groupId: string;
  order: OrderView;
  customers: Customer[];
  creators: Creator[];
  linkedYarnTypes: LinkableYarnType[];
  linkedNeedleTypes: LinkableNeedleType[];
  myYarnInventory: Map<string, number>;
  myNeedleInventory: Map<string, number>;
  onUpdated: (o: OrderView) => void;
}) {
  const customer = customers.find((c) => c.id === order.customerId);

  const logResearchTime = async (hours: number) => {
    onUpdated(await orderTrackerApi.addTimeLogEntry(groupId, order.id, {
      stage: "RESEARCH", hours, date: null, note: null, variantId: null, componentId: null,
    }));
  };

  // Order-level "what you need" list — one combined list across all bulk variants (not
  // per-variant), deduped by kind+value; quantities don't matter here, just presence.
  const itemLabel = (kind: "YARN" | "NEEDLE") => (kind === "YARN" ? "Yarn" : "Needle");
  const dedupeByKeyValue = <T extends { kind: string; value: string }>(items: T[]): T[] => {
    const seen = new Set<string>();
    return items.filter((i) => {
      const k = `${i.kind} ${i.value}`;
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  };
  const allMaterials = dedupeByKeyValue(
    order.orderType === "INDIVIDUAL" ? order.mandatoryItems : (order.bulkDetails?.variants ?? []).flatMap((v) => v.mandatoryItems)
  );
  const allMaterialEntriesRaw =
    order.orderType === "INDIVIDUAL" ? order.mandatoryItems : (order.bulkDetails?.variants ?? []).flatMap((v) => v.mandatoryItems);
  const neededByYarnType = new Map<string, number>();
  const neededByNeedleType = new Map<string, number>();
  allMaterialEntriesRaw.forEach((m) => {
    if (m.linkedYarnTypeId) {
      neededByYarnType.set(m.linkedYarnTypeId, (neededByYarnType.get(m.linkedYarnTypeId) ?? 0) + m.quantity);
    }
    if (m.linkedNeedleTypeId) {
      neededByNeedleType.set(m.linkedNeedleTypeId, (neededByNeedleType.get(m.linkedNeedleTypeId) ?? 0) + m.quantity);
    }
  });
  const yarnTypeLabel = (id: string) => {
    const y = linkedYarnTypes.find((yt) => yt.id === id);
    return y ? `${y.brand} — ${y.thickness}, ${y.colour}` : "linked yarn";
  };
  const needleTypeLabel = (id: string) => {
    const n = linkedNeedleTypes.find((nt) => nt.id === id);
    return n ? `${n.kind === "CROCHET_HOOK" ? "Hook" : "Needle"} ${n.size}` : "linked needle";
  };

  return (
    <div className="grid cols-3">
      <div className="card">
        <h2>Customer</h2>
        <div className="row"><span className="k">Name</span><span className="v">{customer?.name ?? "—"}</span></div>
        <div className="row"><span className="k">Channel</span><span className="v">{customer?.acquisitionChannel ?? "—"}</span></div>
        <div className="row"><span className="k">Logged by</span><span className="v">{creatorName(creators, order.createdByCreatorId)}</span></div>
      </div>

      <div className="card">
        <h2>Pattern</h2>
        {order.pattern ? (
          <>
            <div className="row"><span className="k">Type</span><span className="v">{order.pattern.patternType}</span></div>
            {order.pattern.templateName && (
              <div className="row"><span className="k">Template</span><span className="v">{order.pattern.templateName}</span></div>
            )}
            {order.pattern.customPatternNotes && (
              <div className="row"><span className="k">Notes</span><span className="v">{order.pattern.customPatternNotes}</span></div>
            )}
          </>
        ) : (
          <p className="empty">Not recorded.</p>
        )}
        <div className="row" style={{ marginTop: 8, alignItems: "center", flexWrap: "wrap" }}>
          <span className="k">Research time</span>
          <TimeStageControl
            icon="🔍"
            label="Research"
            estimatedHours={order.researchTimeHours}
            entries={order.timeLogEntries.filter((e) => e.stage === "RESEARCH")}
            onLog={logResearchTime}
          />
        </div>
      </div>

      <div className="card">
        <h2>Recipe</h2>
        {(order.pattern?.recipeSteps ?? []).length === 0 ? (
          <p className="empty">No steps recorded.</p>
        ) : (
          <ol style={{ margin: 0, paddingLeft: 18 }}>
            {(order.pattern?.recipeSteps ?? []).map((s, i) => (
              <li key={i}>{s}</li>
            ))}
          </ol>
        )}
      </div>

      <div className="card">
        <h2>What you need</h2>
        {allMaterials.length === 0 ? (
          <p className="empty">Nothing recorded yet.</p>
        ) : (
          <>
            <div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>
              Materials
            </div>
            <ul style={{ margin: "0 0 10px", paddingLeft: 18 }}>
              {allMaterials.map((m, i) => (
                <li key={i}>
                  {itemLabel(m.kind)}: {m.value}
                </li>
              ))}
            </ul>
            {(neededByYarnType.size > 0 || neededByNeedleType.size > 0) && (
              <>
                <div className="muted" style={{ fontSize: 12, marginTop: 10, marginBottom: 4 }}>
                  Your inventory
                </div>
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {Array.from(neededByYarnType.entries()).map(([yarnTypeId, needed]) => {
                    const have = myYarnInventory.get(yarnTypeId) ?? 0;
                    const short = have < needed;
                    return (
                      <li key={`yarn-${yarnTypeId}`}>
                        {yarnTypeLabel(yarnTypeId)}: you have {have}, need {needed}
                        {short && (
                          <strong style={{ color: "var(--urgent)" }}> — short by {(needed - have).toFixed(2)}</strong>
                        )}
                      </li>
                    );
                  })}
                  {Array.from(neededByNeedleType.entries()).map(([needleTypeId, needed]) => {
                    const have = myNeedleInventory.get(needleTypeId) ?? 0;
                    const short = have < needed;
                    return (
                      <li key={`needle-${needleTypeId}`}>
                        {needleTypeLabel(needleTypeId)}: you have {have}, need {needed}
                        {short && (
                          <strong style={{ color: "var(--urgent)" }}> — short by {(needed - have).toFixed(2)}</strong>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}
