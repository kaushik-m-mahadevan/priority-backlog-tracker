import { useState } from "react";
import { formatMoney } from "../../../lib/format";
import { orderTrackerApi } from "../../api";
import { TimeStageControl } from "../../TimeTracking";
import { creatorName } from "./creatorName";
import { stageIcon } from "./stageIcons";
import type { BusinessConfig, Creator, OrderView } from "../../types";

/** Extracted from OrderDetailPage.tsx (fdup-1) — fully self-contained via props, no
 *  closure over the parent's state. Individual orders show materials/add-ons/packaging +
 *  optional components; bulk orders show the same per variant, plus each variant's
 *  creator split and per-stage progress. */
export function MaterialsSection({
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
  const splitTrackedStages = config.workStages.filter((s) => s.splitTracked);

  const logTime = async (stage: "CRAFTING" | "ASSEMBLY", hours: number, variantId?: string, componentId?: string) => {
    onUpdated(await orderTrackerApi.addTimeLogEntry(groupId, order.id, {
      stage, hours, date: null, note: null, variantId: variantId ?? null, componentId: componentId ?? null,
    }));
  };

  const updateSplitProgress = async (variantId: string, creatorId: string, stageKey: string, unitsCompleted: number) => {
    setError(null);
    try {
      onUpdated(await orderTrackerApi.updateBulkSplitProgress(groupId, order.id, variantId, creatorId, stageKey, unitsCompleted));
    } catch (err) {
      setError(err instanceof Error ? err.message : "That action failed — please retry.");
    }
  };

  if (order.orderType === "INDIVIDUAL") {
    return (
      <>
        {error && <div className="error">{error}</div>}
        <h2 className="settings-section">Materials &amp; packaging</h2>
        <div className="grid cols-3">
          <div className="card">
            <h2>Mandatory items</h2>
            {order.mandatoryItems.length === 0 ? (
              <p className="empty">None recorded.</p>
            ) : (
              order.mandatoryItems.map((m, i) => (
                <div className="row" key={i}>
                  <span className="k">
                    {m.kind === "YARN" ? "Yarn" : "Needle"}: {m.value}
                    {m.notes && <span className="muted"> ({m.notes})</span>}
                  </span>
                  <span className="v">{m.kind === "YARN" ? `${m.quantity} × ${formatMoney(m.unitCost)}` : `${m.quantity}`}</span>
                </div>
              ))
            )}
          </div>
          <div className="card">
            <h2>Add-ons</h2>
            {order.addOns.length === 0 ? (
              <p className="empty">None.</p>
            ) : (
              order.addOns.map((a, i) => (
                <div className="row" key={i}>
                  <span className="k">{a.name}</span>
                  <span className="v">{a.quantity} × {formatMoney(a.unitCost)}</span>
                </div>
              ))
            )}
          </div>
          <div className="card">
            <h2>Packaging</h2>
            <div className="row"><span className="k">Cost</span><span className="v">{order.packaging?.cost != null ? formatMoney(order.packaging.cost) : "₹0.00"}</span></div>
            <div className="row"><span className="k">Time</span><span className="v">{order.packaging?.timeHours ?? 0}h</span></div>
          </div>
        </div>

        {order.components.length > 0 && (
          <>
            <h2 className="settings-section">Components</h2>
            {order.components.map((c) => (
              <div className="card" key={c.componentId} style={{ marginBottom: 10 }}>
                <div className="toolbar">
                  <strong>{c.label}</strong>
                  <span className="muted">× {c.quantity}</span>
                  <span className="spacer" />
                  <span className="muted">{formatMoney(c.perUnitCost)}/unit · {formatMoney(c.totalCost)} total</span>
                </div>
                <div className="row"><span className="k">Materials</span>
                  <span className="v">
                    {c.mandatoryItems.length === 0 ? "None" : c.mandatoryItems.map((m) => `${m.kind === "YARN" ? "Yarn" : "Needle"}: ${m.value}`).join(", ")}
                  </span>
                </div>
                <div className="row"><span className="k">Add-ons</span>
                  <span className="v">{c.addOns.length === 0 ? "None" : c.addOns.map((a) => a.name).join(", ")}</span>
                </div>
                <div className="row" style={{ alignItems: "center", flexWrap: "wrap" }}>
                  <span className="k">Crochet time</span>
                  <TimeStageControl
                    icon="🧶"
                    label={`Crochet — ${c.label}`}
                    estimatedHours={c.perUnitTimeHours * c.quantity}
                    entries={c.timeLogEntries}
                    onLog={(hours) => logTime("CRAFTING", hours, undefined, c.componentId)}
                  />
                </div>
              </div>
            ))}
          </>
        )}

        <h2 className="settings-section">Processes</h2>
        <div className="card">
          {order.components.length === 0 && (
            <div className="row" style={{ alignItems: "center", flexWrap: "wrap" }}>
              <span className="k">Crochet time</span>
              <TimeStageControl
                icon="🧶"
                label="Crochet"
                estimatedHours={order.craftingTimeHours}
                entries={order.timeLogEntries.filter((e) => e.stage === "CRAFTING")}
                onLog={(hours) => logTime("CRAFTING", hours)}
              />
            </div>
          )}
          <div className="row" style={{ alignItems: "center", marginTop: 8, flexWrap: "wrap" }}>
            <span className="k">Assembly time</span>
            <TimeStageControl
              icon="🪡"
              label="Assembly"
              estimatedHours={order.assemblyTimeHours}
              entries={order.timeLogEntries.filter((e) => e.stage === "ASSEMBLY")}
              onLog={(hours) => logTime("ASSEMBLY", hours)}
            />
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      {error && <div className="error">{error}</div>}
      <h2 className="settings-section">Variants</h2>
      <div className="divided-list">
      {order.bulkDetails?.variants.map((v) => (
        <div key={v.variantId}>
          <div className="toolbar">
            <strong>{v.label}</strong>
            <span className="muted">Qty {v.quantity}</span>
            <span className="spacer" />
            <span className="muted">Crochet {v.craftingTimeHours}h/unit · Assembly {v.assemblyTimeHours}h/unit</span>
            <span className="muted" title="Estimated">~{v.perUnitTimeHours.toFixed(2)}h/unit · {v.totalTimeHours.toFixed(2)}h total</span>
            <span title="Estimated">~{formatMoney(v.perUnitCost)}/unit</span>
            <span title="Estimated">Total ~{formatMoney(v.totalCost)}</span>
          </div>
          <div className="toolbar" style={{ marginTop: 8, flexWrap: "wrap", gap: 16 }}>
            {v.components.length === 0 && (
              <TimeStageControl
                icon="🧶"
                label={`Crochet — ${v.label}`}
                estimatedHours={v.craftingTimeHours * v.quantity}
                entries={v.timeLogEntries.filter((e) => e.stage === "CRAFTING")}
                onLog={(hours) => logTime("CRAFTING", hours, v.variantId)}
              />
            )}
            <TimeStageControl
              icon="🪡"
              label={`Assembly — ${v.label}`}
              estimatedHours={v.assemblyTimeHours * v.quantity}
              entries={v.timeLogEntries.filter((e) => e.stage === "ASSEMBLY")}
              onLog={(hours) => logTime("ASSEMBLY", hours, v.variantId)}
            />
          </div>

          {v.components.length > 0 && (
            <div style={{ marginTop: 10 }}>
              <div className="muted" style={{ fontSize: 12, fontWeight: 600, marginBottom: 4 }}>
                Components
              </div>
              {v.components.map((c) => (
                <div key={c.componentId} style={{ marginBottom: 8, paddingBottom: 8, borderBottom: "1px solid var(--border-soft)" }}>
                  <div className="toolbar">
                    <strong>{c.label}</strong>
                    <span className="muted">× {c.quantity}</span>
                    <span className="spacer" />
                    <span className="muted">{formatMoney(c.perUnitCost)}/unit · {formatMoney(c.totalCost)} total</span>
                  </div>
                  <div className="row"><span className="k">Materials</span>
                    <span className="v">
                      {c.mandatoryItems.length === 0 ? "None" : c.mandatoryItems.map((m) => `${m.kind === "YARN" ? "Yarn" : "Needle"}: ${m.value}`).join(", ")}
                    </span>
                  </div>
                  <div className="row"><span className="k">Add-ons</span>
                    <span className="v">{c.addOns.length === 0 ? "None" : c.addOns.map((a) => a.name).join(", ")}</span>
                  </div>
                  <div className="row" style={{ alignItems: "center", flexWrap: "wrap" }}>
                    <span className="k">Crochet time</span>
                    <TimeStageControl
                      icon="🧶"
                      label={`Crochet — ${v.label} — ${c.label}`}
                      estimatedHours={c.perUnitTimeHours * c.quantity}
                      entries={c.timeLogEntries}
                      onLog={(hours) => logTime("CRAFTING", hours, v.variantId, c.componentId)}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="muted" style={{ fontSize: 12, fontWeight: 600, marginTop: 8, marginBottom: 4 }}>
            Materials
          </div>
          <div className="grid cols-3">
            <div>
              <h2>Mandatory items</h2>
              {v.mandatoryItems.length === 0 ? (
                <p className="empty">None.</p>
              ) : (
                v.mandatoryItems.map((m, i) => (
                  <div className="row" key={i}>
                    <span className="k">
                      {m.kind === "YARN" ? "Yarn" : "Needle"}: {m.value}
                      {m.notes && <span className="muted"> ({m.notes})</span>}
                    </span>
                    <span className="v">{m.kind === "YARN" ? `${m.quantity} × ${formatMoney(m.unitCost)}` : `${m.quantity}`}</span>
                  </div>
                ))
              )}
            </div>
            <div>
              <h2>Add-ons</h2>
              {v.addOns.length === 0 ? (
                <p className="empty">None.</p>
              ) : (
                v.addOns.map((a, i) => (
                  <div className="row" key={i}>
                    <span className="k">{a.name}</span>
                    <span className="v">{a.quantity} × {formatMoney(a.unitCost)}</span>
                  </div>
                ))
              )}
            </div>
          </div>
          <div className="muted" style={{ fontSize: 12, fontWeight: 600, margin: "12px 0 4px" }}>
            Processes
          </div>
          <div className="grid cols-2">
            <div>
              <h2>Split across creators</h2>
              {v.splitAllocation.length === 0 ? (
                <p className="empty">Nobody assigned yet.</p>
              ) : (
                v.splitAllocation.map((s) => (
                  <div className="row" key={s.creatorId}>
                    <span className="k">{creatorName(creators, s.creatorId)}</span>
                    <span className="v">{s.quantityAssigned}</span>
                  </div>
                ))
              )}
            </div>
            <div>
              <h2>Progress by stage</h2>
              {v.splitAllocation.length === 0 ? (
                <p className="empty">Nobody assigned yet.</p>
              ) : (
                splitTrackedStages.map((stage) => {
                  const stageKey = stage.stageKey;
                  return (
                  <div key={stageKey} style={{ marginBottom: 10 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 4 }}>
                      <span aria-hidden="true">{stageIcon(stageKey)}</span> {stage.label}
                    </div>
                    {v.splitAllocation.map((s) => {
                      const entry = s.stageProgress.find((sp) => sp.stageKey === stageKey);
                      return (
                        <div key={s.creatorId} className="row" style={{ paddingLeft: 22 }}>
                          <span className="k">{creatorName(creators, s.creatorId)}</span>
                          <input
                            aria-label={`${stage.label} units completed by ${creatorName(creators, s.creatorId)}`}
                            type="number"
                            min={0}
                            max={s.quantityAssigned}
                            style={{ width: 70 }}
                            value={entry?.unitsCompleted ?? 0}
                            onChange={(e) => updateSplitProgress(v.variantId, s.creatorId, stageKey, Number(e.target.value))}
                          />
                        </div>
                      );
                    })}
                  </div>
                  );
                })
              )}
            </div>
          </div>
        </div>
      ))}
      </div>
    </>
  );
}
