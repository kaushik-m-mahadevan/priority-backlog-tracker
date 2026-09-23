import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { formatDate, formatMoney } from "../../lib/format";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import AddToGroupModal from "../AddToGroupModal";
import { Section } from "../../components/Section";
import { OrderDueDate } from "../OrderDueDate";
import ImageGallery from "../../components/ImageGallery";
import { ProgressRing } from "../../components/ProgressRing";
import { TimeStageControl } from "../TimeTracking";
import { assemblyHoursForBulk, assemblyHoursForIndividual, bulkCreatorEtas, craftHoursForBulk, craftHoursForIndividual, hoursPct } from "../orderProgress";
import { useLinkedNeedleTypes } from "../useLinkedNeedleTypes";
import { useLinkedYarnTypes } from "../useLinkedYarnTypes";
import { useMyNeedleInventory } from "../useMyNeedleInventory";
import { useMyYarnInventory } from "../useMyYarnInventory";
import { EditOrderForm } from "./orderdetail/EditOrderForm";
import { EditBulkDetailsForm } from "./orderdetail/EditBulkDetailsForm";
import { FinalizationCard } from "./orderdetail/FinalizationCard";
import { ShippingCard } from "./orderdetail/ShippingCard";
import { DELIVERY_TIER_LABELS } from "./orderdetail/deliveryTiers";
import CancelOrderModal from "../CancelOrderModal";
import { legalMoves } from "../orderStatusColumns";
import type { BusinessConfig, Creator, Customer, OrderView, PaymentType, PresetOption, TimeStage } from "../types";

/** The two stage keys with real per-creator split tracking — named constants instead of
 *  the string literal duplicated between STAGE_ICONS and the hoursBased check below, so a
 *  rename can't silently desync the two (mirrors BusinessConfig.STAGE_CROCHETING/
 *  STAGE_ASSEMBLY on the backend). */
const STAGE_CROCHETING = "crocheting";
const STAGE_ASSEMBLY = "assembly";

/** Work stages are configurable per business, so this is a best-effort visual cue for the
 *  common ones rather than a strict mapping — an unrecognized stageKey still gets a
 *  sensible generic icon rather than nothing. */
const STAGE_ICONS: Record<string, string> = {
  [STAGE_CROCHETING]: "🧶",
  [STAGE_ASSEMBLY]: "🧵",
  packaging: "📦",
  shipment: "🚚",
};
function stageIcon(stageKey: string): string {
  return STAGE_ICONS[stageKey.toLowerCase()] ?? "🔧";
}



export default function OrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const linkedYarnTypes = useLinkedYarnTypes(groupId);
  const linkedNeedleTypes = useLinkedNeedleTypes(groupId);
  const myYarnInventory = useMyYarnInventory(groupId);
  const myNeedleInventory = useMyNeedleInventory(groupId);
  const [order, setOrder] = useState<OrderView | null>(null);
  const [config, setConfig] = useState<BusinessConfig | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [creators, setCreators] = useState<Creator[]>([]);
  const [presets, setPresets] = useState<PresetOption[]>([]);
  const [editing, setEditing] = useState(false);
  const [paymentAmount, setPaymentAmount] = useState(0);
  const [paymentType, setPaymentType] = useState<PaymentType>("ADVANCE");
  const [paymentMode, setPaymentMode] = useState("UPI");
  const [paymentModeOther, setPaymentModeOther] = useState("");
  const [paymentReceivedBy, setPaymentReceivedBy] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [addingToGroup, setAddingToGroup] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [historyCreatorFilter, setHistoryCreatorFilter] = useState("all");
  const [historyStageFilter, setHistoryStageFilter] = useState("all");
  const [usageYarnId, setUsageYarnId] = useState("");
  const [usageQty, setUsageQty] = useState("");
  const [usageNote, setUsageNote] = useState("");

  const load = () => {
    Promise.all([
      orderTrackerApi.order(groupId, orderId!),
      orderTrackerApi.businessConfig(groupId),
      orderTrackerApi.customers(groupId),
      orderTrackerApi.creators(groupId),
      orderTrackerApi.packagingPresets(groupId),
    ]).then(([o, cfg, c, cr, p]) => {
      setOrder(o);
      setConfig(cfg);
      setCustomers(c);
      setCreators(cr);
      setPresets(p);
    });
  };

  useEffect(load, [groupId, orderId]);

  if (!order || !config) return <p className="muted">Loading…</p>;

  const customer = customers.find((c) => c.id === order.customerId);
  const creatorName = (id: string | null | undefined) => {
    if (!id) return <span className="muted">Unassigned</span>;
    return creators.find((c) => c.id === id)?.name ?? <span className="muted">Unknown creator</span>;
  };
  const dueDate = order.orderType === "INDIVIDUAL" ? order.costEstimate?.computedDueDate : order.bulkDetails?.computedDueDate;
  const splitTrackedStages = config.workStages.filter((s) => s.splitTracked);

  const stageLabel = (stage: TimeStage) =>
    stage === "RESEARCH" ? "Research" : stage === "CRAFTING" ? "Crochet" : "Assembly";

  type HistoryRow = {
    entryId: string; stage: TimeStage; hours: number; date: string; loggedByCreatorId: string;
    variantLabel: string | null; componentLabel: string | null;
  };
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

  // Hours-based completion for crocheting/assembly (the two stages with real time
  // tracking) — packaging/shipment have no hour estimate, so they stay on the existing
  // units-completed/total-units mechanism instead. Math lives in orderProgress.ts (tf-4)
  // so it's testable without rendering this page.
  const { logged: craftLoggedIndividual, estimated: craftEstimatedIndividual } = craftHoursForIndividual(order);
  const craftPctIndividual = hoursPct(craftLoggedIndividual, craftEstimatedIndividual);
  const { logged: assemblyLoggedIndividual } = assemblyHoursForIndividual(order);
  const assemblyPctIndividual = hoursPct(assemblyLoggedIndividual, order.assemblyTimeHours);

  const { logged: craftLoggedBulk, estimated: craftEstimatedBulk } = craftHoursForBulk(order);
  const craftPctBulk = hoursPct(craftLoggedBulk, craftEstimatedBulk);
  const { logged: assemblyLoggedBulk, estimated: assemblyEstimatedBulk } = assemblyHoursForBulk(order);
  const assemblyPctBulk = hoursPct(assemblyLoggedBulk, assemblyEstimatedBulk);

  const logTime = async (stage: TimeStage, hours: number, variantId?: string, componentId?: string) => {
    const updated = await orderTrackerApi.addTimeLogEntry(groupId, order.id, {
      stage, hours, date: null, note: null, variantId: variantId ?? null, componentId: componentId ?? null,
    });
    setOrder(updated);
  };
  const removeTime = async (entryId: string) => {
    const updated = await orderTrackerApi.removeTimeLogEntry(groupId, order.id, entryId);
    setOrder(updated);
  };

  const logUsage = async (yarnTypeId: string, quantity: number, note: string) => {
    const updated = await orderTrackerApi.addUsageLogEntry(groupId, order.id, {
      yarnTypeId, quantity, date: null, note: note.trim() || null,
    });
    setOrder(updated);
  };
  const removeUsage = async (entryId: string) => {
    const updated = await orderTrackerApi.removeUsageLogEntry(groupId, order.id, entryId);
    setOrder(updated);
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
  // Shortfall check (design decision: tackled now, opt-in via linkedYarnTypeId /
  // linkedNeedleTypeId) — sums needed quantity across every material entry sharing an
  // inventory type, not deduped like allMaterials above, since two variants each needing
  // some can genuinely need more combined than either alone. Only ever compares against
  // the viewer's own on-hand count (design decision), never a cross-member total.
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

  // Every mutation below the summary bar (status, stage progress, payments) runs through
  // this so a failed call surfaces a real error instead of failing invisibly. Returns
  // whether it succeeded, so a caller can skip clearing its own form fields on failure.
  const runAction = async (fn: () => Promise<OrderView>): Promise<boolean> => {
    setActionError(null);
    try {
      setOrder(await fn());
      return true;
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "That action failed — please retry.");
      return false;
    }
  };

  return (
    <div>
      {/* Sticky (ui-10): this order's status/edit actions are the ones you reach for
          while reading the (often long) sections below, so they stay reachable instead
          of forcing a scroll back to the top every time. */}
      <div className="order-sticky-actions">
        {actionError && <div className="error" style={{ marginBottom: 12 }}>{actionError}</div>}
        <div className="toolbar" style={{ marginBottom: 4 }}>
          <span className="mono" style={{ fontSize: 20, fontWeight: 700 }}>
            {order.orderNumber}
          </span>
          <span className="badge">{order.orderType}</span>
          {order.status === "CANCELLED" ? (
            <span className="badge" style={{ color: "var(--urgent)" }}>CANCELLED</span>
          ) : (
            <select
              aria-label="Order status"
              value={order.status}
              onChange={async (e) => {
                const target = e.target.value;
                const move = legalMoves(order.status).find((m) => m.status === target);
                let justification: string | undefined;
                if (move?.needsJustification) {
                  const entered = window.prompt(
                    `Moving this from ${order.status.replace(/_/g, " ")} back to ${target.replace(/_/g, " ")} — what happened? ` +
                      "(e.g. Item damaged, Rework needed, Customer changed request)"
                  );
                  if (entered === null) return; // cancelled the prompt
                  if (!entered.trim()) {
                    setActionError("A reason is required to move this back");
                    return;
                  }
                  justification = entered.trim();
                }
                runAction(() => orderTrackerApi.updateStatus(groupId, order.id, target, justification));
              }}
            >
              <option value={order.status}>{order.status.replace(/_/g, " ")}</option>
              {legalMoves(order.status).map((m) => (
                <option key={m.status} value={m.status}>
                  {m.status.replace(/_/g, " ")}
                  {m.needsJustification ? " (needs a reason)" : ""}
                </option>
              ))}
            </select>
          )}
          <span className="badge">{order.paymentStatus.replace(/_/g, " ")}</span>
          <span className="spacer" />
          <button onClick={() => setAddingToGroup(true)}>Add to Priority Tracker</button>
          {!editing && order.status !== "CANCELLED" && (
            <button onClick={() => setEditing(true)}>Edit order</button>
          )}
          {order.status !== "CANCELLED" && (
            <button className="ghost" onClick={() => setCancelling(true)}>
              Cancel order
            </button>
          )}
        </div>
        <p className="page-sub">{order.itemName}</p>
      </div>
      {addingToGroup && <AddToGroupModal order={order} onClose={() => setAddingToGroup(false)} />}
      {cancelling && (
        <CancelOrderModal
          groupId={groupId}
          order={order}
          onCancelled={(o) => setOrder(o)}
          onClose={() => setCancelling(false)}
        />
      )}

      <div className="toolbar" style={{ marginBottom: 20 }}>
        <div className="order-progress-track">
          <div className="order-progress-fill" style={{ width: `${Math.min(100, order.completionPercentage)}%` }} />
        </div>
        <span className="mono" style={{ fontSize: 13, minWidth: 44, textAlign: "right" }}>
          {order.completionPercentage.toFixed(0)}%
        </span>
      </div>

      {editing && order.orderType === "INDIVIDUAL" && (
        <EditOrderForm
          groupId={groupId}
          order={order}
          config={config}
          presets={presets}
          customers={customers}
          onSaved={(o) => {
            setOrder(o);
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
        />
      )}
      {editing && order.orderType === "BULK" && (
        <EditBulkDetailsForm
          groupId={groupId}
          order={order}
          config={config}
          creators={creators}
          customers={customers}
          onSaved={(o) => {
            setOrder(o);
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
        />
      )}

      <Section title="Summary" icon="📋">
      <div className="grid cols-3">
        <div className="card">
          <h2>Customer</h2>
          <div className="row"><span className="k">Name</span><span className="v">{customer?.name ?? "—"}</span></div>
          <div className="row"><span className="k">Channel</span><span className="v">{customer?.acquisitionChannel ?? "—"}</span></div>
          <div className="row"><span className="k">Logged by</span><span className="v">{creatorName(order.createdByCreatorId)}</span></div>
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
              onLog={(hours) => logTime("RESEARCH", hours)}
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
      </Section>

      {order.orderType === "INDIVIDUAL" ? (
        <Section title="Materials" icon="🧶">
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
        </Section>
      ) : (
        <Section title="Materials" icon="🧶">
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
                        <span className="k">{creatorName(s.creatorId)}</span>
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
                              <span className="k">{creatorName(s.creatorId)}</span>
                              <input
                                aria-label={`${stage.label} units completed by ${creatorName(s.creatorId)}`}
                                type="number"
                                min={0}
                                max={s.quantityAssigned}
                                style={{ width: 70 }}
                                value={entry?.unitsCompleted ?? 0}
                                onChange={async (e) =>
                                  runAction(() =>
                                    orderTrackerApi.updateBulkSplitProgress(
                                      groupId, order.id, v.variantId, s.creatorId, stageKey, Number(e.target.value)
                                    )
                                  )
                                }
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
        </Section>
      )}

      {order.orderType === "BULK" && (
        <Section title="Assignments" icon="👥">
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
                    onChange={async (e) =>
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
            {(() => {
              const etas = bulkCreatorEtas(order, creators);
              if (etas.length === 0) {
                return <p className="empty">Nobody assigned yet.</p>;
              }
              return etas.map(({ creatorId, hours, hoursPerDay, targetDate }) => (
                <div className="row" key={creatorId}>
                  <span className="k">{creatorName(creatorId)}</span>
                  <span className="v">
                    {hours.toFixed(1)}h at {hoursPerDay}h/day
                    {targetDate && <> — target {formatDate(targetDate.toISOString())}</>}
                  </span>
                </div>
              ));
            })()}
          </div>
        </Section>
      )}

      {order.orderType === "INDIVIDUAL" && (
        <Section title="Assignments" icon="👥">
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
                  <span className="k">{s.stageKey} — {creatorName(s.assignedCreatorId)}</span>
                </span>
                {hoursBased === null && (
                  <label style={{ display: "flex", gap: 6, alignItems: "center" }}>
                    <input
                      type="checkbox"
                      checked={s.unitsCompleted >= 1}
                      onChange={async (e) =>
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
        </Section>
      )}

      <Section title="Cost & time" icon="💰">
        <div className="card cost-card" style={{ background: "var(--bg-elev-2)" }}>
          <h2>Cost &amp; time — estimate</h2>
          <p className="muted" style={{ fontSize: 12, marginTop: -4, marginBottom: 10 }}>
            Calculated from what's entered above. Not a final invoice — record the real numbers once the order ships.
          </p>
          {order.orderType === "INDIVIDUAL" && order.costEstimate ? (
            <>
              {config && !config.hourlyWageConfirmed && (
                <p className="hint" style={{ marginTop: 0 }}>
                  ⚠ Labor is priced at the default rate ({config.currency} {config.hourlyWage}/h) — confirm your
                  business's real rate in Business Settings.
                </p>
              )}
              {order.costEstimate.itemizedBreakdown.map((b) => (
                <div className="row" key={b.label}><span className="k">{b.label}</span><span className="v">{formatMoney(b.amount)}</span></div>
              ))}
              <div className="cost-total"><span className="k">Estimated price</span><span className="v">{formatMoney(order.costEstimate.finalCost)}</span></div>
              <div className="row" style={{ marginTop: 8 }}><span className="k">Estimated time</span><span className="v">{order.costEstimate.grossTimeHours}h</span></div>
            </>
          ) : (
            <>
              <div className="row"><span className="k">Total quantity</span><span className="v">{order.bulkDetails?.totalQuantity}</span></div>
              <div className="cost-total"><span className="k">Estimated total cost</span><span className="v">{formatMoney(order.bulkDetails?.totalFinalCost ?? 0)}</span></div>
              <div className="row" style={{ marginTop: 8 }}><span className="k">Estimated total time</span><span className="v">{order.bulkDetails?.totalTimeHours}h</span></div>
            </>
          )}

          <div className="row" style={{ marginTop: 12 }}><span className="k">Shipping to</span>
            <span className="v">{DELIVERY_TIER_LABELS[order.deliveryTier]}</span></div>
          {order.orderType === "INDIVIDUAL" && order.costEstimate && (
            <>
              <div className="row"><span className="k">Work days</span>
                <span className="v">{order.costEstimate.workDays}d (allocated {order.costEstimate.grossTimeHours}h ÷ hours/day)</span></div>
              <div className="row"><span className="k">Delivery buffer</span>
                <span className="v">+{order.costEstimate.deliveryBufferDays}d</span></div>
              {config && (
                <div className="row"><span className="k">Time overhead</span>
                  <span className="v">×{(1 + config.overheadPercentage).toFixed(2)}</span></div>
              )}
            </>
          )}
          <div className="row"><span className="k">Estimated delivery</span>
            <span className="v"><OrderDueDate iso={dueDate ?? null} status={order.status} /></span></div>
          {order.quotedDeliveryDate && (
            <div className="row"><span className="k">Quoted to customer</span>
              <span className="v"><OrderDueDate iso={order.quotedDeliveryDate} status={order.status} /></span></div>
          )}
        </div>
      </Section>


      <Section title="Finalization" icon="✅">
        <FinalizationCard groupId={groupId} order={order} />
      </Section>

      <Section title="Photos" icon="📷">
        <div className="card">
          <h2>Finished product photos</h2>
          <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
            One shared gallery for the whole order (design decision) — even a bulk order with several colorways
            keeps one combined set of photos here, same as the materials summary above.
          </p>
          <ImageGallery groupId={groupId} ownerType="order" ownerId={order.id} />
        </div>
      </Section>

      <Section title="Logistics" icon="🚚">
        <div className="card">
          <h2>Payment</h2>
          {order.payments.length === 0 ? (
            <p className="empty">No payments yet.</p>
          ) : (
            order.payments.map((p) => (
              <div className="row" key={p.paymentId}>
                <span className="k">{p.type}</span>
                <span className="v">
                  {formatMoney(p.amount)} ({p.mode})
                  <button
                    type="button"
                    className="linkbtn"
                    style={{ marginLeft: 8, fontSize: 12 }}
                    onClick={() => {
                      if (window.confirm(`Remove this ${formatMoney(p.amount)} ${p.type.toLowerCase()} payment?`)) {
                        runAction(() => orderTrackerApi.removePayment(groupId, order.id, p.paymentId));
                      }
                    }}
                  >
                    remove
                  </button>
                </span>
              </div>
            ))
          )}
          <div className="row">
            <span className="k">Balance</span>
            {order.balanceAmount < 0 ? (
              <span className="v" style={{ color: "var(--urgent)", fontWeight: 600 }}>
                Over budget by {formatMoney(Math.abs(order.balanceAmount))}
              </span>
            ) : (
              <span className="v">{formatMoney(order.balanceAmount)}</span>
            )}
          </div>
          <div className="toolbar" style={{ marginTop: 10 }}>
            <select aria-label="Payment type" value={paymentType} onChange={(e) => setPaymentType(e.target.value as PaymentType)}>
              <option value="ADVANCE">Advance</option>
              <option value="INSTALLMENT">Installment</option>
              <option value="FINAL">Final</option>
              <option value="REFUND">Refund</option>
            </select>
            <input aria-label="Payment amount" type="number" placeholder="Amount" value={paymentAmount || ""} onChange={(e) => setPaymentAmount(Number(e.target.value))} />
            <select aria-label="Payment mode" value={paymentMode} onChange={(e) => setPaymentMode(e.target.value)}>
              <option value="UPI">UPI</option>
              <option value="CASH">Cash</option>
              <option value="BANK_TRANSFER">Bank transfer</option>
              <option value="CARD">Card</option>
              <option value="OTHER">Other</option>
            </select>
            {paymentMode === "OTHER" && (
              <input aria-label="Payment mode (other)" placeholder="Describe how" value={paymentModeOther}
                onChange={(e) => setPaymentModeOther(e.target.value)} />
            )}
            <select aria-label="Received by" value={paymentReceivedBy} onChange={(e) => setPaymentReceivedBy(e.target.value)}>
              <option value="">Received by: me</option>
              {creators.map((c) => (
                <option key={c.id} value={c.userId}>{creatorName(c.id)}</option>
              ))}
              <option value="BUSINESS">Business Account</option>
            </select>
            <button
              className="primary"
              disabled={paymentMode === "OTHER" && !paymentModeOther.trim()}
              onClick={async () => {
                if (!paymentAmount) return;
                const mode = paymentMode === "OTHER" ? paymentModeOther.trim() : paymentMode;
                const ok = await runAction(() => orderTrackerApi.addPayment(groupId, order.id, {
                  type: paymentType, amount: paymentAmount, mode,
                  receivedBy: paymentReceivedBy || undefined,
                }));
                if (ok) {
                  setPaymentAmount(0);
                  setPaymentModeOther("");
                  setPaymentReceivedBy("");
                }
              }}
            >
              Record
            </button>
          </div>
        </div>

        <ShippingCard groupId={groupId} order={order} onUpdated={setOrder} />
      </Section>

      <Section title="Time History" icon="🕒" defaultOpen={false}>
        {allTimeEntries.length === 0 ? (
          <p className="empty">No time logged yet.</p>
        ) : (
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
                        <td>{creatorName(e.loggedByCreatorId)}</td>
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
        )}
      </Section>

      {linkedYarnTypes.length > 0 && (
        <Section title="Usage Log" icon="🧶" defaultOpen={false}>
          <p className="hint">
            Log actual yarn used, any time, independent of order status — reserves eat first,
            then your own stash. {order.status !== "CANCELLED" ? "" : "This order is cancelled; logged entries stay as a historical record."}
          </p>
          {order.status !== "CANCELLED" && (
            <form
              className="toolbar"
              onSubmit={(e) => {
                e.preventDefault();
                const qty = Number(usageQty);
                if (!usageYarnId || !qty || qty <= 0) return;
                logUsage(usageYarnId, qty, usageNote).then(() => {
                  setUsageQty("");
                  setUsageNote("");
                });
              }}
            >
              <select aria-label="Yarn used" value={usageYarnId} onChange={(e) => setUsageYarnId(e.target.value)} required>
                <option value="">Yarn…</option>
                {linkedYarnTypes.map((y) => (
                  <option key={y.id} value={y.id}>{y.brand} — {y.thickness}, {y.colour}</option>
                ))}
              </select>
              <input
                aria-label="Quantity (skeins)"
                type="number" min={0.25} step={0.25} placeholder="Skeins"
                style={{ width: 90 }}
                value={usageQty}
                onChange={(e) => setUsageQty(e.target.value)}
              />
              <input
                aria-label="Note"
                placeholder="Note (optional)"
                value={usageNote}
                onChange={(e) => setUsageNote(e.target.value)}
              />
              <button className="primary" type="submit">Log usage</button>
            </form>
          )}
          {order.usageLogEntries.length === 0 ? (
            <p className="empty">No usage logged yet.</p>
          ) : (
            <div className="table-wrap">
              <table className="ot-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Yarn</th>
                    <th>Skeins</th>
                    <th>Logged by</th>
                    <th>Synced</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {order.usageLogEntries.map((e) => (
                    <tr key={e.entryId}>
                      <td className="cell-subtitle">{new Date(e.date).toLocaleDateString()}</td>
                      <td className="muted">{yarnTypeLabel(e.yarnTypeId)}</td>
                      <td className="cell-order mono">{e.quantity}</td>
                      <td>{creatorName(e.loggedByCreatorId)}</td>
                      <td>{e.synced ? "✓" : "pending"}</td>
                      <td>
                        <button
                          type="button"
                          className="linkbtn"
                          onClick={() => {
                            if (window.confirm(`Remove this ${e.quantity}-skein usage entry?`)) {
                              removeUsage(e.entryId);
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
        </Section>
      )}

      <Section title="Notes" icon="📝">
        <div className="card">
          {order.notes ? (
            <p style={{ whiteSpace: "pre-wrap", margin: 0 }}>{order.notes}</p>
          ) : (
            <p className="empty">Nothing noted.</p>
          )}
          {order.assemblyPackagingInstructions && (
            <>
              <div className="muted" style={{ fontSize: 12, marginTop: 12, marginBottom: 4 }}>
                Assembly/packaging how-to (from before Components existed)
              </div>
              <p style={{ whiteSpace: "pre-wrap", margin: 0 }}>{order.assemblyPackagingInstructions}</p>
            </>
          )}
        </div>
      </Section>

      {order.cancellation && (
        <Section title="Cancellation" icon="🚫">
          <div className="card">
            <div className="row"><span className="k">Reason</span><span className="v">{order.cancellation.reason}</span></div>
            {order.cancellation.note && (
              <div className="row"><span className="k">Note</span><span className="v">{order.cancellation.note}</span></div>
            )}
            <div className="row"><span className="k">Cancelled</span><span className="v">{formatDate(order.cancellation.cancelledAt)}</span></div>
            {(order.cancellation.estimatedMaterialsLoss > 0 || order.cancellation.estimatedLaborLoss > 0) && (
              <>
                <p className="muted" style={{ fontSize: 12, marginTop: 10, marginBottom: 4 }}>
                  Estimated loss (informational only — not a ledger entry)
                </p>
                <div className="row"><span className="k">Materials at risk</span><span className="v">{formatMoney(order.cancellation.estimatedMaterialsLoss)}</span></div>
                <div className="row"><span className="k">Unrecovered labor</span><span className="v">{formatMoney(order.cancellation.estimatedLaborLoss)}</span></div>
              </>
            )}
          </div>
        </Section>
      )}
    </div>
  );
}
