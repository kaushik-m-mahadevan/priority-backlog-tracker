import { useState } from "react";
import { formatDate } from "../../../lib/format";
import { orderTrackerApi } from "../../api";
import { ProgressRing } from "../../../components/ProgressRing";
import { assemblyHoursForBulk, assemblyHoursForIndividual, bulkCreatorEtas, craftHoursForBulk, craftHoursForIndividual, hoursPct } from "../../orderProgress";
import { creatorName } from "./creatorName";
import { STAGE_ASSEMBLY, STAGE_CROCHETING, stageIcon } from "./stageIcons";
import type { BusinessConfig, Creator, OrderView } from "../../types";

/** Extracted from OrderDetailPage.tsx (fdup-1) — fully self-contained via props, no
 *  closure over the parent's state. Bulk orders show batch-tracked stage progress plus
 *  each contributor's own ETA; individual orders show each work stage, hours-based for
 *  crocheting/assembly (the two stages with real time tracking) and a plain done checkbox
 *  for everything else. */
export function AssignmentsSection({
  groupId,
  order,
  creators,
  config,
  onUpdated,
}: {
  groupId: string;
  order: OrderView;
  creators: Creator[];
  config: BusinessConfig;
  onUpdated: (o: OrderView) => void;
}) {
  const [error, setError] = useState<string | null>(null);

  const runAction = async (fn: () => Promise<OrderView>) => {
    setError(null);
    try {
      onUpdated(await fn());
    } catch (err) {
      setError(err instanceof Error ? err.message : "That action failed — please retry.");
    }
  };

  if (order.orderType === "BULK") {
    const { logged: craftLoggedBulk, estimated: craftEstimatedBulk } = craftHoursForBulk(order);
    const craftPctBulk = hoursPct(craftLoggedBulk, craftEstimatedBulk);
    const { logged: assemblyLoggedBulk, estimated: assemblyEstimatedBulk } = assemblyHoursForBulk(order);
    const assemblyPctBulk = hoursPct(assemblyLoggedBulk, assemblyEstimatedBulk);
    const etas = bulkCreatorEtas(order, creators);

    return (
      <>
        {error && <div className="error">{error}</div>}
        <div className="card" style={{ marginBottom: 16 }}>
          <h2>Crocheting &amp; assembly (hours logged vs. estimated)</h2>
          <div className="row" style={{ alignItems: "center" }}>
            <span style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <ProgressRing percent={craftPctBulk} />
              <span className="k"><span aria-hidden="true">🧶</span> Crocheting</span>
            </span>
            <span className="muted">{craftLoggedBulk.toFixed(2)}h / {craftEstimatedBulk.toFixed(2)}h</span>
          </div>
          <div className="row" style={{ alignItems: "center" }}>
            <span style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <ProgressRing percent={assemblyPctBulk} />
              <span className="k"><span aria-hidden="true">🪡</span> Assembly</span>
            </span>
            <span className="muted">{assemblyLoggedBulk.toFixed(2)}h / {assemblyEstimatedBulk.toFixed(2)}h</span>
          </div>
        </div>

        <div className="card" style={{ marginBottom: 16 }}>
          <h2>Batch-tracked stages</h2>
          {order.bulkDetails?.stageProgress.map((sp) => {
            const stageLabel = config.workStages.find((s) => s.stageKey === sp.stageKey)?.label ?? sp.stageKey;
            const pct = sp.totalUnits > 0 ? (sp.unitsCompleted / sp.totalUnits) * 100 : 0;
            return (
            <div className="row" key={sp.stageKey} style={{ alignItems: "center" }}>
              <span style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <ProgressRing percent={pct} />
                <span className="k">
                  <span aria-hidden="true">{stageIcon(sp.stageKey)}</span> {stageLabel}
                </span>
              </span>
              <span className="v">
                <input
                  aria-label={`${stageLabel} units completed`}
                  type="number"
                  min={0}
                  max={sp.totalUnits}
                  style={{ width: 90 }}
                  value={sp.unitsCompleted}
                  onChange={(e) =>
                    runAction(() => orderTrackerApi.updateBulkStageProgress(groupId, order.id, sp.stageKey, Number(e.target.value)))
                  }
                />
                <span className="muted"> / {sp.totalUnits}</span>
              </span>
            </div>
            );
          })}
        </div>

        <div className="card" style={{ marginBottom: 16 }}>
          <h2>Contributor timelines — estimate</h2>
          <p className="muted" style={{ fontSize: 12, marginTop: -4, marginBottom: 10 }}>
            Each creator's own target finish date, based on their assigned hours and their own pace.
          </p>
          {etas.length === 0 ? (
            <p className="empty">Nobody assigned yet.</p>
          ) : (
            etas.map(({ creatorId, hours, hoursPerDay, targetDate }) => (
              <div className="row" key={creatorId}>
                <span className="k">{creatorName(creators, creatorId)}</span>
                <span className="v">
                  {hours.toFixed(1)}h at {hoursPerDay}h/day
                  {targetDate && <> — target {formatDate(targetDate.toISOString())}</>}
                </span>
              </div>
            ))
          )}
        </div>
      </>
    );
  }

  const { logged: craftLoggedIndividual, estimated: craftEstimatedIndividual } = craftHoursForIndividual(order);
  const craftPctIndividual = hoursPct(craftLoggedIndividual, craftEstimatedIndividual);
  const { logged: assemblyLoggedIndividual } = assemblyHoursForIndividual(order);
  const assemblyPctIndividual = hoursPct(assemblyLoggedIndividual, order.assemblyTimeHours);

  return (
    <>
      {error && <div className="error">{error}</div>}
      <div className="card" style={{ marginBottom: 16 }}>
        <h2>Work stages</h2>
        {order.stageAssignments.map((s) => {
          const hoursBased = s.stageKey === STAGE_CROCHETING ? craftPctIndividual
            : s.stageKey === STAGE_ASSEMBLY ? assemblyPctIndividual : null;
          const pct = hoursBased ?? (s.totalUnits > 0 ? (s.unitsCompleted / s.totalUnits) * 100 : 0);
          return (
            <div className="row" key={s.stageKey} style={{ alignItems: "center" }}>
              <span style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <ProgressRing percent={pct} />
                <span className="k">{s.stageKey} — {creatorName(creators, s.assignedCreatorId)}</span>
              </span>
              {hoursBased === null && (
                <label style={{ display: "flex", gap: 6, alignItems: "center" }}>
                  <input
                    type="checkbox"
                    checked={s.unitsCompleted >= 1}
                    onChange={(e) =>
                      runAction(() =>
                        orderTrackerApi.updateStageAssignment(
                          groupId, order.id, s.stageKey, s.assignedCreatorId, e.target.checked ? 1 : 0
                        )
                      )
                    }
                  />
                  Done
                </label>
              )}
            </div>
          );
        })}
      </div>
    </>
  );
}
