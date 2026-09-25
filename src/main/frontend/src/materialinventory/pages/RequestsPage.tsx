import { useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { formatDateTime } from "../../lib/format";
import { materialInventoryApi } from "../api";
import { useMaterialInventory } from "../MaterialInventoryContext";
import type { InventoryEntryView, TransferLineView, TransferRequestView, YarnTypeView } from "../types";

type DraftLine = { yarnTypeId: string; quantity: string };
type NewRequestDraft = { targetUserId: string; lines: DraftLine[] };

const blankLine = (): DraftLine => ({ yarnTypeId: "", quantity: "" });
const blankDraft = (): NewRequestDraft => ({ targetUserId: "", lines: [blankLine()] });

/** A targeted ask for yarn from one specific member to another (design decision — not a
 *  broadcast), covering one or more yarn types in a single ask. Sending and confirming
 *  receipt are two separate steps (see TransferRequest's own doc comment): the target's
 *  "Send" debits their own on-hand right away, but the requester's own inventory only
 *  credits once they separately confirm a specific shipment actually arrived — the yarn is
 *  in neither party's stash while it's genuinely in transit. A line's own status (not one
 *  status for the whole request) is what actually gates further sends: the requester can
 *  close a line at any time (even after only part of it arrived) without touching a
 *  shipment already sent, and the target can still send more against an open line later. */
export default function RequestsPage() {
  const { currentInventoryGroup, currentGroupId } = useMaterialInventory();
  const { user } = useAuth();
  const [requests, setRequests] = useState<TransferRequestView[]>([]);
  const [yarnTypes, setYarnTypes] = useState<YarnTypeView[]>([]);
  const [inventory, setInventory] = useState<InventoryEntryView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [draft, setDraft] = useState<NewRequestDraft>(blankDraft());
  const [sendDraft, setSendDraft] = useState<Record<string, { quantity: string; notes: string }>>({});
  const [busyKey, setBusyKey] = useState<string | null>(null);

  const members = currentInventoryGroup?.members ?? [];
  const memberName = (id: string) => (id === user?.id ? "You" : members.find((m) => m.id === id)?.name ?? "Unknown");
  const yarnLabel = (id: string) => {
    const y = yarnTypes.find((yt) => yt.id === id);
    return y ? `${y.brand} — ${y.thickness}, ${y.colour}` : "Unknown yarn";
  };

  const load = () => {
    if (!currentGroupId) return;
    setLoading(true);
    Promise.all([
      materialInventoryApi.transfers(currentGroupId),
      materialInventoryApi.yarnTypes(currentGroupId),
      materialInventoryApi.inventory(currentGroupId),
    ])
      .then(([reqs, types, inv]) => {
        setRequests(reqs);
        setYarnTypes(types);
        setInventory(inv);
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  const quantityOf = (userId: string, yarnTypeId: string) =>
    inventory.find((e) => e.userId === userId && e.yarnTypeId === yarnTypeId)?.quantity ?? 0;

  /** Who could actually fulfill every yarn type currently drafted, most-stocked-overall
   *  first — a real availability check (not just a display hint), so the "Ask" list never
   *  offers someone who plainly doesn't have enough of something already in the draft. */
  const eligibleTargets = () => {
    const needed = draft.lines.filter((l) => l.yarnTypeId && Number(l.quantity) > 0);
    return members
      .filter((m) => m.id !== user?.id)
      .filter((m) => needed.every((l) => quantityOf(m.id, l.yarnTypeId) + QUARTER_EPSILON >= Number(l.quantity)))
      .sort((a, b) => {
        const totalFor = (id: string) => needed.reduce((sum, l) => sum + quantityOf(id, l.yarnTypeId), 0);
        return totalFor(b.id) - totalFor(a.id);
      });
  };

  /** Availability hint shown under a single yarn-type line, regardless of the other lines
   *  in the draft — someone might be a great fit for this one yarn type even if they can't
   *  cover everything in the request. */
  const availabilityFor = (yarnTypeId: string) =>
    members
      .filter((m) => m.id !== user?.id && quantityOf(m.id, yarnTypeId) > QUARTER_EPSILON)
      .sort((a, b) => quantityOf(b.id, yarnTypeId) - quantityOf(a.id, yarnTypeId));

  const isLineActive = (l: TransferLineView) => l.status === "OPEN" || l.shipments.some((s) => s.receivedAt === null);
  const isRequestActive = (r: TransferRequestView) => r.lines.some(isLineActive);
  const active = requests.filter(isRequestActive);
  const settled = requests.filter((r) => !isRequestActive(r))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  const setDraftLine = (i: number, patch: Partial<DraftLine>) => {
    const lines = draft.lines.map((l, idx) => (idx === i ? { ...l, ...patch } : l));
    setDraft({ ...draft, lines });
  };
  const addDraftLine = () => setDraft({ ...draft, lines: [...draft.lines, blankLine()] });
  const removeDraftLine = (i: number) => setDraft({ ...draft, lines: draft.lines.filter((_, idx) => idx !== i) });

  const submitRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentGroupId) return;
    const lines = draft.lines
      .filter((l) => l.yarnTypeId)
      .map((l) => ({ yarnTypeId: l.yarnTypeId, quantity: Number(l.quantity) }));
    if (!draft.targetUserId || lines.length === 0 || lines.some((l) => !l.quantity || l.quantity <= 0)) {
      setError("Pick who to ask, at least one yarn type, and a quantity greater than zero for each");
      return;
    }
    setError(null);
    try {
      await materialInventoryApi.createTransferRequest(currentGroupId, { targetUserId: draft.targetUserId, lines });
      setDraft(blankDraft());
      setShowForm(false);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create the request");
    }
  };

  const send = async (requestId: string, lineId: string) => {
    if (!currentGroupId) return;
    const key = `${requestId}:${lineId}`;
    const draftForLine = sendDraft[key];
    const qty = Number(draftForLine?.quantity);
    if (!qty || qty <= 0) {
      setError("Enter how much yarn you want to send");
      return;
    }
    setError(null);
    setBusyKey(key);
    try {
      await materialInventoryApi.sendShipment(currentGroupId, requestId, lineId, {
        quantity: qty,
        notes: draftForLine?.notes?.trim() || null,
      });
      const rest = { ...sendDraft };
      delete rest[key];
      setSendDraft(rest);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send");
    } finally {
      setBusyKey(null);
    }
  };

  const confirmReceived = async (requestId: string, lineId: string, shipmentId: string) => {
    if (!currentGroupId) return;
    setError(null);
    setBusyKey(shipmentId);
    try {
      await materialInventoryApi.confirmShipmentReceived(currentGroupId, requestId, lineId, shipmentId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to confirm receipt");
    } finally {
      setBusyKey(null);
    }
  };

  const closeLine = async (requestId: string, lineId: string) => {
    if (!currentGroupId) return;
    if (!window.confirm("Stop expecting any more of this yarn type on this request?")) return;
    setError(null);
    setBusyKey(lineId);
    try {
      await materialInventoryApi.closeTransferLine(currentGroupId, requestId, lineId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to close");
    } finally {
      setBusyKey(null);
    }
  };

  const cancelRequest = async (requestId: string) => {
    if (!currentGroupId) return;
    setError(null);
    setBusyKey(requestId);
    try {
      await materialInventoryApi.cancelTransferRequest(currentGroupId, requestId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cancel");
    } finally {
      setBusyKey(null);
    }
  };

  const renderLine = (r: TransferRequestView, l: TransferLineView) => {
    const isTarget = r.targetUserId === user?.id;
    const isRequester = r.requesterId === user?.id;
    const remaining = l.requestedQuantity - l.sentQuantity;
    const key = `${r.id}:${l.lineId}`;
    return (
      <div key={l.lineId} style={{ marginTop: 10, paddingTop: 10, borderTop: "1px solid var(--border-soft)" }}>
        <div className="row">
          <span className="k">{yarnLabel(l.yarnTypeId)}</span>
          <span className="v">
            {l.receivedQuantity} received / {l.sentQuantity} sent / {l.requestedQuantity} requested
            {l.status === "CLOSED" && <span className="badge" style={{ marginLeft: 6 }}>closed</span>}
          </span>
        </div>

        {l.shipments.length > 0 && (
          <div style={{ marginTop: 6 }}>
            {l.shipments.map((s) => (
              <div className="row" key={s.shipmentId} style={{ paddingLeft: 12 }}>
                <span className="k muted">
                  {s.quantity} sent {formatDateTime(s.sentAt)}
                  {s.notes && ` — "${s.notes}"`}
                </span>
                <span className="v">
                  {s.receivedAt ? (
                    <span className="muted">received {formatDateTime(s.receivedAt)}</span>
                  ) : isRequester ? (
                    <button type="button" disabled={busyKey === s.shipmentId}
                      onClick={() => confirmReceived(r.id, l.lineId, s.shipmentId)}>
                      Mark received
                    </button>
                  ) : (
                    <span className="muted">in transit</span>
                  )}
                </span>
              </div>
            ))}
          </div>
        )}

        {isTarget && l.status === "OPEN" && remaining > QUARTER_EPSILON && (
          <div className="toolbar" style={{ flexWrap: "wrap", marginTop: 6 }}>
            <label htmlFor={`send-qty-${key}`} className="sr-only">How much do you want to send?</label>
            <input
              id={`send-qty-${key}`}
              aria-label={`How much ${yarnLabel(l.yarnTypeId)} do you want to send`}
              type="number"
              min={0.25}
              step={0.25}
              max={remaining}
              placeholder={`Up to ${remaining}`}
              style={{ width: 110 }}
              value={sendDraft[key]?.quantity ?? ""}
              onChange={(e) => setSendDraft({ ...sendDraft, [key]: { ...sendDraft[key], quantity: e.target.value, notes: sendDraft[key]?.notes ?? "" } })}
            />
            <input
              aria-label="Notes — how it's being sent"
              placeholder="e.g. sent via Blue Dart AWB123, handed to them in person"
              style={{ flex: 1, minWidth: 200 }}
              value={sendDraft[key]?.notes ?? ""}
              onChange={(e) => setSendDraft({ ...sendDraft, [key]: { ...sendDraft[key], notes: e.target.value, quantity: sendDraft[key]?.quantity ?? "" } })}
            />
            <button type="button" className="primary" disabled={busyKey === key} onClick={() => send(r.id, l.lineId)}>
              Send yarn
            </button>
          </div>
        )}

        {isRequester && l.status === "OPEN" && (
          <div className="toolbar" style={{ marginTop: 6 }}>
            <button type="button" className="linkbtn" disabled={busyKey === l.lineId} onClick={() => closeLine(r.id, l.lineId)}>
              {remaining > QUARTER_EPSILON ? "Close — don't need the rest" : "Close this line"}
            </button>
          </div>
        )}
      </div>
    );
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
            <p className="hint" style={{ marginTop: 0 }}>
              Pick what you need first — who you can ask depends on who actually has it.
            </p>
            {draft.lines.map((line, i) => {
              const available = line.yarnTypeId ? availabilityFor(line.yarnTypeId) : [];
              return (
                <div key={i} style={{ marginTop: i === 0 ? 0 : 12, paddingTop: i === 0 ? 0 : 12, borderTop: i === 0 ? undefined : "1px solid var(--border-soft)" }}>
                  <div className="form-grid">
                    <div className="form-row">
                      <label htmlFor={`req-yarn-${i}`}>For</label>
                      <select id={`req-yarn-${i}`} value={line.yarnTypeId}
                        onChange={(e) => setDraftLine(i, { yarnTypeId: e.target.value })} required>
                        <option value="">Select…</option>
                        {yarnTypes.map((y) => (
                          <option key={y.id} value={y.id}>
                            {y.brand} — {y.thickness}, {y.colour}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="form-row">
                      <label htmlFor={`req-qty-${i}`}>Quantity</label>
                      <div className="toolbar">
                        <input
                          id={`req-qty-${i}`}
                          type="number"
                          min={0.25}
                          step={0.25}
                          value={line.quantity}
                          onChange={(e) => setDraftLine(i, { quantity: e.target.value })}
                          required
                        />
                        {draft.lines.length > 1 && (
                          <button type="button" className="ghost" onClick={() => removeDraftLine(i)} aria-label="Remove this yarn type">
                            ✕
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                  {line.yarnTypeId && (
                    <p className="muted" style={{ fontSize: 12, marginTop: 4 }}>
                      {available.length === 0
                        ? "No one else in this group has any of this yarn type."
                        : "Available: " + available.map((m) => `${memberName(m.id)} (${quantityOf(m.id, line.yarnTypeId)})`).join(", ")}
                    </p>
                  )}
                </div>
              );
            })}
            <div className="toolbar" style={{ marginTop: 8 }}>
              <button type="button" className="ghost" onClick={addDraftLine}>
                + Another yarn type
              </button>
            </div>

            <div className="form-row" style={{ marginTop: 12 }}>
              <label htmlFor="req-target">Ask</label>
              <select
                id="req-target"
                value={draft.targetUserId}
                onChange={(e) => setDraft({ ...draft, targetUserId: e.target.value })}
                required
              >
                <option value="">Select…</option>
                {eligibleTargets().map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name}
                  </option>
                ))}
              </select>
              {eligibleTargets().length === 0 && draft.lines.some((l) => l.yarnTypeId && Number(l.quantity) > 0) && (
                <p className="hint bad" style={{ marginTop: 4 }}>
                  No one in this group currently has enough of everything above.
                </p>
              )}
            </div>

            <div className="toolbar" style={{ marginTop: 12 }}>
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
        ) : active.length === 0 ? (
          <p className="empty" style={{ marginTop: 12 }}>Nothing pending.</p>
        ) : (
          active.map((r) => {
            const isRequester = r.requesterId === user?.id;
            const canCancelWhole = isRequester && r.lines.every((l) => l.shipments.length === 0);
            return (
              <div className="card" key={r.id} style={{ background: "var(--bg-elev-2)", marginTop: 12 }}>
                <div className="row">
                  <span className="k">{memberName(r.requesterId)} asked {memberName(r.targetUserId)}</span>
                  <span className="v muted">{formatDateTime(r.createdAt)}</span>
                </div>
                {r.lines.filter(isLineActive).map((l) => renderLine(r, l))}
                {canCancelWhole && (
                  <div className="toolbar" style={{ marginTop: 10 }}>
                    <button type="button" disabled={busyKey === r.id} onClick={() => cancelRequest(r.id)}>
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
        {settled.length === 0 ? (
          <p className="empty">No settled requests yet.</p>
        ) : (
          settled.map((r) => (
            <div className="card" key={r.id} style={{ marginTop: 12 }}>
              <div className="row">
                <span className="k">{memberName(r.requesterId)} asked {memberName(r.targetUserId)}</span>
                <span className="v muted">{formatDateTime(r.createdAt)}</span>
              </div>
              {r.lines.map((l) => (
                <div className="row" key={l.lineId} style={{ paddingLeft: 12 }}>
                  <span className="k muted">{yarnLabel(l.yarnTypeId)}</span>
                  <span className="v mono">{l.receivedQuantity} / {l.requestedQuantity}</span>
                </div>
              ))}
            </div>
          ))
        )}
      </div>
    </div>
  );
}

const QUARTER_EPSILON = 1e-9;
