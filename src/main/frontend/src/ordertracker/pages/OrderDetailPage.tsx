import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import AddToGroupModal from "../AddToGroupModal";
import { Section } from "../../components/Section";
import ImageGallery from "../../components/ImageGallery";
import { formatDate, formatMoney } from "../../lib/format";
import { useLinkedNeedleTypes } from "../useLinkedNeedleTypes";
import { useLinkedYarnTypes } from "../useLinkedYarnTypes";
import { useMyNeedleInventory } from "../useMyNeedleInventory";
import { useMyYarnInventory } from "../useMyYarnInventory";
import { EditOrderForm } from "./orderdetail/EditOrderForm";
import { EditBulkDetailsForm } from "./orderdetail/EditBulkDetailsForm";
import { SummaryCard } from "./orderdetail/SummaryCard";
import { MaterialsSection } from "./orderdetail/MaterialsSection";
import { AssignmentsSection } from "./orderdetail/AssignmentsSection";
import { CostTimeSection } from "./orderdetail/CostTimeSection";
import { FinalizationCard } from "./orderdetail/FinalizationCard";
import { LogisticsSection } from "./orderdetail/LogisticsSection";
import { TimeHistorySection } from "./orderdetail/TimeHistorySection";
import { UsageLogSection } from "./orderdetail/UsageLogSection";
import CancelOrderModal from "../CancelOrderModal";
import { legalMoves } from "../orderStatusColumns";
import type { BusinessConfig, Creator, Customer, OrderView, PresetOption } from "../types";

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
  const [actionError, setActionError] = useState<string | null>(null);
  const [addingToGroup, setAddingToGroup] = useState(false);
  const [cancelling, setCancelling] = useState(false);

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
        <SummaryCard
          groupId={groupId}
          order={order}
          customers={customers}
          creators={creators}
          linkedYarnTypes={linkedYarnTypes}
          linkedNeedleTypes={linkedNeedleTypes}
          myYarnInventory={myYarnInventory}
          myNeedleInventory={myNeedleInventory}
          onUpdated={setOrder}
        />
      </Section>

      <Section title="Materials" icon="🧶">
        <MaterialsSection groupId={groupId} order={order} creators={creators} config={config} onUpdated={setOrder} />
      </Section>

      <Section title="Assignments" icon="👥">
        <AssignmentsSection groupId={groupId} order={order} creators={creators} config={config} onUpdated={setOrder} />
      </Section>

      <Section title="Cost & time" icon="💰">
        <CostTimeSection order={order} config={config} />
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
        <LogisticsSection groupId={groupId} order={order} creators={creators} onUpdated={setOrder} />
      </Section>

      <Section title="Time History" icon="🕒" defaultOpen={false}>
        <TimeHistorySection groupId={groupId} order={order} creators={creators} onUpdated={setOrder} />
      </Section>

      {linkedYarnTypes.length > 0 && (
        <Section title="Usage Log" icon="🧶" defaultOpen={false}>
          <UsageLogSection groupId={groupId} order={order} creators={creators} linkedYarnTypes={linkedYarnTypes} onUpdated={setOrder} />
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
