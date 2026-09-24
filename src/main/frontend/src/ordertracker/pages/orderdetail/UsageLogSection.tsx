import { useState } from "react";
import { orderTrackerApi } from "../../api";
import { creatorName } from "./creatorName";
import type { LinkableYarnType } from "../../OrderFormFields";
import type { Creator, OrderView } from "../../types";

/** Extracted from OrderDetailPage.tsx (fdup-1) — fully self-contained via props, no
 *  closure over the parent's state. Only ever rendered by the parent when this business
 *  has at least one linked yarn type (opt-in — see the parent's own gate on
 *  linkedYarnTypes.length). */
export function UsageLogSection({
  groupId,
  order,
  creators,
  linkedYarnTypes,
  onUpdated,
}: {
  groupId: string;
  order: OrderView;
  creators: Creator[];
  linkedYarnTypes: LinkableYarnType[];
  onUpdated: (o: OrderView) => void;
}) {
  const [usageYarnId, setUsageYarnId] = useState("");
  const [usageQty, setUsageQty] = useState("");
  const [usageNote, setUsageNote] = useState("");

  const yarnTypeLabel = (id: string) => {
    const y = linkedYarnTypes.find((yt) => yt.id === id);
    return y ? `${y.brand} — ${y.thickness}, ${y.colour}` : "linked yarn";
  };

  const logUsage = async (yarnTypeId: string, quantity: number, note: string) => {
    onUpdated(await orderTrackerApi.addUsageLogEntry(groupId, order.id, {
      yarnTypeId, quantity, date: null, note: note.trim() || null,
    }));
  };
  const removeUsage = async (entryId: string) => {
    onUpdated(await orderTrackerApi.removeUsageLogEntry(groupId, order.id, entryId));
  };

  return (
    <>
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
                  <td>{creatorName(creators, e.loggedByCreatorId)}</td>
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
    </>
  );
}
