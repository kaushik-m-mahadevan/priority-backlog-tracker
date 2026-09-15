import { useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { materialInventoryApi } from "../api";
import { useMaterialInventory } from "../MaterialInventoryContext";
import type { TransferRequestView, YarnTypeView } from "../types";

type NewRequestDraft = { targetUserId: string; yarnTypeId: string; requestedQuantity: string };

const blankDraft = (): NewRequestDraft => ({ targetUserId: "", yarnTypeId: "", requestedQuantity: "" });

/** A targeted ask for yarn from one specific member to another (design decision — not a
 *  broadcast). Partial fulfillment is allowed (design decision): the target can hand over
 *  yarn in installments, and only the target decides when to mark the whole request
 *  complete (design decision) — reaching the requested amount doesn't auto-complete it. */
export default function RequestsPage() {
  const { currentInventoryGroup, currentGroupId } = useMaterialInventory();
  const { user } = useAuth();
  const [requests, setRequests] = useState<TransferRequestView[]>([]);
  const [yarnTypes, setYarnTypes] = useState<YarnTypeView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [draft, setDraft] = useState<NewRequestDraft>(blankDraft());
  const [fulfillAmount, setFulfillAmount] = useState<Record<string, string>>({});
  const [busyId, setBusyId] = useState<string | null>(null);

  const members = currentInventoryGroup?.members ?? [];
  const memberName = (id: string) => (id === user?.id ? "You" : members.find((m) => m.id === id)?.name ?? "Unknown");
  const yarnLabel = (id: string) => {
    const y = yarnTypes.find((yt) => yt.id === id);
    return y ? `${y.brand} — ${y.thickness}, ${y.colour}` : "Unknown yarn";
  };

  const load = () => {
    if (!currentGroupId) return;
    setLoading(true);
    Promise.all([materialInventoryApi.transfers(currentGroupId), materialInventoryApi.yarnTypes(currentGroupId)])
      .then(([reqs, types]) => {
        setRequests(reqs);
        setYarnTypes(types);
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  const open = requests.filter((r) => r.status === "PENDING" || r.status === "PARTIALLY_FULFILLED");
  const resolved = requests
    .filter((r) => r.status === "COMPLETED" || r.status === "CANCELLED")
    .sort((a, b) => (b.resolvedAt ?? b.createdAt).localeCompare(a.resolvedAt ?? a.createdAt));

  const submitRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentGroupId) return;
    const qty = Number(draft.requestedQuantity);
    if (!draft.targetUserId || !draft.yarnTypeId || !qty || qty <= 0) {
      setError("Pick who to ask, which yarn, and a quantity greater than zero");
      return;
    }
    setError(null);
    try {
      await materialInventoryApi.createTransferRequest(currentGroupId, {
        targetUserId: draft.targetUserId,
        yarnTypeId: draft.yarnTypeId,
        requestedQuantity: qty,
      });
      setDraft(blankDraft());
      setShowForm(false);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create the request");
    }
  };

  const fulfill = async (requestId: string) => {
    if (!currentGroupId) return;
    const qty = Number(fulfillAmount[requestId]);
    if (!qty || qty <= 0) {
      setError("Enter a quantity greater than zero");
      return;
    }
    setError(null);
    setBusyId(requestId);
    try {
      await materialInventoryApi.fulfillTransferRequest(currentGroupId, requestId, { quantity: qty });
      const rest = { ...fulfillAmount };
      delete rest[requestId];
      setFulfillAmount(rest);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record fulfillment");
    } finally {
      setBusyId(null);
    }
  };

  const complete = async (requestId: string) => {
    if (!currentGroupId) return;
    setError(null);
    setBusyId(requestId);
    try {
      await materialInventoryApi.completeTransferRequest(currentGroupId, requestId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to mark complete");
    } finally {
      setBusyId(null);
    }
  };

  const cancel = async (requestId: string) => {
    if (!currentGroupId) return;
    setError(null);
    setBusyId(requestId);
    try {
      await materialInventoryApi.cancelTransferRequest(currentGroupId, requestId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cancel");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <h1 className="page-title">Requests</h1>
      <p className="page-sub">{currentInventoryGroup?.name}</p>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <div className="toolbar">
          <h2 style={{ margin: 0 }}>Open requests</h2>
          <span className="spacer" />
          {!showForm && (
            <button type="button" onClick={() => setShowForm(true)}>
              + Ask for yarn
            </button>
          )}
        </div>

        {showForm && (
          <form onSubmit={submitRequest} style={{ marginTop: 12 }}>
            <div className="form-grid">
              <div className="form-row">
                <label htmlFor="req-target">Ask</label>
                <select
                  id="req-target"
                  value={draft.targetUserId}
                  onChange={(e) => setDraft({ ...draft, targetUserId: e.target.value })}
                  required
                >
                  <option value="">Select…</option>
                  {members.filter((m) => m.id !== user?.id).map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-row">
                <label htmlFor="req-yarn">For</label>
                <select id="req-yarn" value={draft.yarnTypeId} onChange={(e) => setDraft({ ...draft, yarnTypeId: e.target.value })} required>
                  <option value="">Select…</option>
                  {yarnTypes.map((y) => (
                    <option key={y.id} value={y.id}>
                      {y.brand} — {y.thickness}, {y.colour}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-row">
                <label htmlFor="req-qty">Quantity</label>
                <input
                  id="req-qty"
                  type="number"
                  min={0.25}
                  step={0.25}
                  value={draft.requestedQuantity}
                  onChange={(e) => setDraft({ ...draft, requestedQuantity: e.target.value })}
                  required
                />
              </div>
            </div>
            <div className="toolbar">
              <button className="primary" type="submit">
                Send request
              </button>
              <button type="button" onClick={() => { setShowForm(false); setDraft(blankDraft()); }}>
                Cancel
              </button>
            </div>
          </form>
        )}

        {loading ? (
          <p className="muted" style={{ marginTop: 12 }}>Loading…</p>
        ) : open.length === 0 ? (
          <p className="empty" style={{ marginTop: 12 }}>Nothing pending.</p>
        ) : (
          open.map((r) => {
            const isTarget = r.targetUserId === user?.id;
            const isRequester = r.requesterId === user?.id;
            const remaining = r.requestedQuantity - r.fulfilledQuantity;
            return (
              <div className="card" key={r.id} style={{ background: "var(--bg-elev-2)", marginTop: 12 }}>
                <div className="row"><span className="k">{memberName(r.requesterId)} asked {memberName(r.targetUserId)}</span><span className="v">{yarnLabel(r.yarnTypeId)}</span></div>
                <div className="row"><span className="k">Requested</span><span className="v mono">{r.requestedQuantity}</span></div>
                <div className="row"><span className="k">Given so far</span><span className="v mono">{r.fulfilledQuantity}</span></div>
                <p className="muted" style={{ fontSize: 13 }}>{r.status.replace(/_/g, " ")} — {remaining > 0 ? `${remaining} still requested` : "fully given"}</p>

                {isTarget && (
                  <div className="toolbar" style={{ flexWrap: "wrap" }}>
                    <input
                      aria-label="Fulfill quantity"
                      type="number"
                      min={0.25}
                      step={0.25}
                      style={{ width: 90 }}
                      value={fulfillAmount[r.id] ?? ""}
                      onChange={(e) => setFulfillAmount({ ...fulfillAmount, [r.id]: e.target.value })}
                    />
                    <button type="button" disabled={busyId === r.id} onClick={() => fulfill(r.id)}>
                      Give yarn
                    </button>
                    <button type="button" disabled={busyId === r.id} onClick={() => complete(r.id)}>
                      Mark complete
                    </button>
                  </div>
                )}
                {isRequester && (
                  <div className="toolbar">
                    <button type="button" disabled={busyId === r.id} onClick={() => cancel(r.id)}>
                      Cancel request
                    </button>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>History</h2>
        {resolved.length === 0 ? (
          <p className="empty">No resolved requests yet.</p>
        ) : (
          <div className="table-wrap">
            <table className="ot-table">
              <thead>
                <tr>
                  <th>From</th>
                  <th>To</th>
                  <th>Yarn</th>
                  <th>Given</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {resolved.map((r) => (
                  <tr key={r.id}>
                    <td className="cell-subtitle">{memberName(r.targetUserId)}</td>
                    <td className="cell-subtitle">{memberName(r.requesterId)}</td>
                    <td className="cell-title">{yarnLabel(r.yarnTypeId)}</td>
                    <td className="cell-order mono">{r.fulfilledQuantity} / {r.requestedQuantity}</td>
                    <td className="cell-type">{r.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
