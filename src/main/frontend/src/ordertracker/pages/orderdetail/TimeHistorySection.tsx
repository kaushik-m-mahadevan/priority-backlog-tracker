import { useState } from "react";
import { orderTrackerApi } from "../../api";
import { creatorName } from "./creatorName";
import type { Creator, OrderView, TimeStage } from "../../types";

type HistoryRow = {
  entryId: string; stage: TimeStage; hours: number; date: string; loggedByCreatorId: string;
  variantLabel: string | null; componentLabel: string | null;
};

const stageLabel = (stage: TimeStage) =>
  stage === "RESEARCH" ? "Research" : stage === "CRAFTING" ? "Crochet" : "Assembly";

/** Extracted from OrderDetailPage.tsx (fdup-1) — fully self-contained via props, no
 *  closure over the parent's state. Flattens every time-log entry across the order,
 *  components, and (for bulk) variants/their components into one filterable table. */
export function TimeHistorySection({
  groupId,
  order,
  creators,
  onUpdated,
}: {
  groupId: string;
  order: OrderView;
  creators: Creator[];
  onUpdated: (o: OrderView) => void;
}) {
  const [historyCreatorFilter, setHistoryCreatorFilter] = useState("all");
  const [historyStageFilter, setHistoryStageFilter] = useState("all");

  const allTimeEntries: HistoryRow[] = [
    ...order.timeLogEntries.map((e) => ({ ...e, variantLabel: null as string | null, componentLabel: null as string | null })),
    ...order.components.flatMap((c) =>
      c.timeLogEntries.map((e) => ({ ...e, variantLabel: null as string | null, componentLabel: c.label }))
    ),
    ...(order.bulkDetails?.variants.flatMap((v) => [
      ...v.timeLogEntries.map((e) => ({ ...e, variantLabel: v.label as string | null, componentLabel: null as string | null })),
      ...v.components.flatMap((c) =>
        c.timeLogEntries.map((e) => ({ ...e, variantLabel: v.label as string | null, componentLabel: c.label }))
      ),
    ]) ?? []),
  ];
  const filteredHistory = allTimeEntries
    .filter((e) => historyCreatorFilter === "all" || e.loggedByCreatorId === historyCreatorFilter)
    .filter((e) => historyStageFilter === "all" || e.stage === historyStageFilter)
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
  const creatorsWithEntries = creators.filter((c) => allTimeEntries.some((e) => e.loggedByCreatorId === c.id));

  const removeTime = async (entryId: string) => {
    onUpdated(await orderTrackerApi.removeTimeLogEntry(groupId, order.id, entryId));
  };

  if (allTimeEntries.length === 0) {
    return <p className="empty">No time logged yet.</p>;
  }

  return (
    <>
      <div className="toolbar" style={{ marginBottom: 10 }}>
        <select aria-label="Filter by person" value={historyCreatorFilter} onChange={(e) => setHistoryCreatorFilter(e.target.value)}>
          <option value="all">Everyone</option>
          {creatorsWithEntries.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
        <select aria-label="Filter by stage" value={historyStageFilter} onChange={(e) => setHistoryStageFilter(e.target.value)}>
          <option value="all">All stages</option>
          <option value="RESEARCH">Research</option>
          <option value="CRAFTING">Crochet</option>
          <option value="ASSEMBLY">Assembly</option>
        </select>
      </div>
      {filteredHistory.length === 0 ? (
        <p className="empty">No entries match this filter.</p>
      ) : (
        <div className="table-wrap">
          <table className="ot-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Stage</th>
                <th>Variant</th>
                <th>Component</th>
                <th>Hours</th>
                <th>Logged by</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filteredHistory.map((e) => (
                <tr key={e.entryId}>
                  <td className="cell-subtitle">{new Date(e.date).toLocaleDateString()}</td>
                  <td>{stageLabel(e.stage)}</td>
                  <td className="muted">{e.variantLabel ?? "—"}</td>
                  <td className="muted">{e.componentLabel ?? "—"}</td>
                  <td className="cell-order mono">{e.hours.toFixed(2)}h</td>
                  <td>{creatorName(creators, e.loggedByCreatorId)}</td>
                  <td>
                    <button
                      type="button"
                      className="linkbtn"
                      onClick={() => {
                        if (window.confirm(`Remove this ${e.hours.toFixed(2)}h ${stageLabel(e.stage)} entry?`)) {
                          removeTime(e.entryId);
                        }
                      }}
                    >
                      remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
