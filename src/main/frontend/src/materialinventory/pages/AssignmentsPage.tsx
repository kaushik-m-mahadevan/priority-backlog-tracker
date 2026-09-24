import { useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { materialInventoryApi } from "../api";
import { useMaterialInventory } from "../MaterialInventoryContext";
import type { MaterialAssignmentView, YarnTypeView } from "../types";

type NewAssignmentDraft = { recipientId: string; yarnTypeId: string; quantity: string };

const blankDraft = (): NewAssignmentDraft => ({ recipientId: "", yarnTypeId: "", quantity: "" });

/** mb-21: propose/accept yarn hand-off between two named members, chosen over a direct set
 *  specifically for the audit trail — e.g. "I'm shipping 5 skeins to Bangalore" is proposed
 *  (debiting the proposer's own on-hand right away, since it's no longer really theirs from
 *  that point), and the receiving member accepts before it lands on their own row. */
export default function AssignmentsPage() {
  const { currentInventoryGroup, currentGroupId } = useMaterialInventory();
  const { user } = useAuth();
  const [assignments, setAssignments] = useState<MaterialAssignmentView[]>([]);
  const [yarnTypes, setYarnTypes] = useState<YarnTypeView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [draft, setDraft] = useState<NewAssignmentDraft>(blankDraft());
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
    Promise.all([materialInventoryApi.assignments(currentGroupId), materialInventoryApi.yarnTypes(currentGroupId)])
      .then(([a, types]) => {
        setAssignments(a);
        setYarnTypes(types);
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [currentGroupId]);

  const pending = assignments.filter((a) => a.status === "PENDING");
  const resolved = assignments
    .filter((a) => a.status !== "PENDING")
    .sort((a, b) => (b.resolvedAt ?? b.createdAt).localeCompare(a.resolvedAt ?? a.createdAt));

  const submitProposal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentGroupId) return;
    const qty = Number(draft.quantity);
    if (!draft.recipientId || !draft.yarnTypeId || !qty || qty <= 0) {
      setError("Pick who to send it to, which yarn, and a quantity greater than zero");
      return;
    }
    setError(null);
    try {
      await materialInventoryApi.proposeAssignment(currentGroupId, {
        recipientId: draft.recipientId,
        yarnTypeId: draft.yarnTypeId,
        quantity: qty,
      });
      setDraft(blankDraft());
      setShowForm(false);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to propose the assignment");
    }
  };

  const respond = async (assignmentId: string, action: "accept" | "reject" | "cancel") => {
    if (!currentGroupId) return;
    setError(null);
    setBusyId(assignmentId);
    try {
      if (action === "accept") await materialInventoryApi.acceptAssignment(currentGroupId, assignmentId);
      else if (action === "reject") await materialInventoryApi.rejectAssignment(currentGroupId, assignmentId);
      else await materialInventoryApi.cancelAssignment(currentGroupId, assignmentId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to respond");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <h1 className="page-title">Assignments</h1>
      <p className="page-sub">{currentInventoryGroup?.name}</p>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <div className="toolbar">
          <h2 style={{ margin: 0 }}>Pending assignments</h2>
          <span className="spacer" />
          {!showForm && (
            <button type="button" onClick={() => setShowForm(true)}>
              + Assign yarn to someone
            </button>
          )}
        </div>

        {showForm && (
          <form onSubmit={submitProposal} style={{ marginTop: 12 }}>
            <p className="hint" style={{ marginTop: 0 }}>
              Proposing debits your own on-hand right away — the recipient's row only updates once they accept.
            </p>
            <div className="form-grid">
              <div className="form-row">
                <label htmlFor="assign-recipient">Send to</label>
                <select
                  id="assign-recipient"
                  value={draft.recipientId}
                  onChange={(e) => setDraft({ ...draft, recipientId: e.target.value })}
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
                <label htmlFor="assign-yarn">Yarn</label>
                <select id="assign-yarn" value={draft.yarnTypeId} onChange={(e) => setDraft({ ...draft, yarnTypeId: e.target.value })} required>
                  <option value="">Select…</option>
                  {yarnTypes.map((y) => (
                    <option key={y.id} value={y.id}>
                      {y.brand} — {y.thickness}, {y.colour}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-row">
                <label htmlFor="assign-qty">Quantity</label>
                <input
                  id="assign-qty"
                  type="number"
                  min={0.25}
                  step={0.25}
                  value={draft.quantity}
                  onChange={(e) => setDraft({ ...draft, quantity: e.target.value })}
                  required
                />
              </div>
            </div>
            <div className="toolbar">
              <button className="primary" type="submit">
                Propose
              </button>
              <button type="button" onClick={() => { setShowForm(false); setDraft(blankDraft()); }}>
                Cancel
              </button>
            </div>
          </form>
        )}

        {loading ? (
          <p className="muted" style={{ marginTop: 12 }}>Loading…</p>
        ) : pending.length === 0 ? (
          <p className="empty" style={{ marginTop: 12 }}>Nothing pending.</p>
        ) : (
          pending.map((a) => {
            const isRecipient = a.recipientId === user?.id;
            const isProposer = a.proposerId === user?.id;
            return (
              <div className="card" key={a.id} style={{ background: "var(--bg-elev-2)", marginTop: 12 }}>
                <div className="row"><span className="k">{memberName(a.proposerId)} → {memberName(a.recipientId)}</span><span className="v">{yarnLabel(a.yarnTypeId)}</span></div>
                <div className="row"><span className="k">Quantity</span><span className="v mono">{a.quantity}</span></div>
                <p className="muted" style={{ fontSize: 13 }}>
                  {isRecipient ? "Waiting for you to accept." : "Waiting for the recipient to accept."}
                </p>

                {isRecipient && (
                  <div className="toolbar">
                    <button className="primary" type="button" disabled={busyId === a.id} onClick={() => respond(a.id, "accept")}>
                      Accept
                    </button>
                    <button type="button" disabled={busyId === a.id} onClick={() => respond(a.id, "reject")}>
                      Reject
                    </button>
                  </div>
                )}
                {isProposer && (
                  <div className="toolbar">
                    <button type="button" disabled={busyId === a.id} onClick={() => respond(a.id, "cancel")}>
                      Cancel
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
          <p className="empty">No resolved assignments yet.</p>
        ) : (
          <div className="table-wrap">
            <table className="ot-table">
              <thead>
                <tr>
                  <th>From</th>
                  <th>To</th>
                  <th>Yarn</th>
                  <th>Quantity</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {resolved.map((a) => (
                  <tr key={a.id}>
                    <td className="cell-subtitle">{memberName(a.proposerId)}</td>
                    <td className="cell-subtitle">{memberName(a.recipientId)}</td>
                    <td className="cell-title">{yarnLabel(a.yarnTypeId)}</td>
                    <td className="cell-order mono">{a.quantity}</td>
                    <td className="cell-type">{a.status}</td>
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
