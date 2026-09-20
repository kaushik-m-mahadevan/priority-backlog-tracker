import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { formatDate } from "../../lib/format";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import { OrderDueDate } from "../OrderDueDate";
import type { AcquisitionChannel, Customer, CustomerAddress, OrderView } from "../types";

const CHANNELS: AcquisitionChannel[] = ["INSTAGRAM", "WHATSAPP", "REFERRAL", "WORD_OF_MOUTH", "WALK_IN", "OTHER"];

/** Draft shape for one address row — addressId is null for a not-yet-saved entry, same
 *  convention as bulk order variants (ad-6). */
type AddressDraft = { addressId: string | null; label: string; address: string; isDefault: boolean };

const blankAddressDraft = (): AddressDraft => ({ addressId: null, label: "", address: "", isDefault: false });

interface Draft {
  name: string;
  contactNumber: string;
  email: string;
  instagramHandle: string;
  acquisitionChannel: AcquisitionChannel;
  firstContactDate: string;
  addresses: AddressDraft[];
  notes: string;
}

const toDraft = (c: Customer): Draft => ({
  name: c.name,
  contactNumber: c.contactNumber ?? "",
  email: c.email ?? "",
  instagramHandle: c.instagramHandle ?? "",
  acquisitionChannel: c.acquisitionChannel,
  firstContactDate: c.firstContactDate ? c.firstContactDate.slice(0, 10) : "",
  addresses: c.addresses.map((a) => ({ addressId: a.addressId, label: a.label, address: a.address, isDefault: a.isDefault })),
  notes: c.notes ?? "",
});

/** Every customer's own page — contact details (editable in place), every order they've
 *  ever placed (pulled from the same order list the Orders page already fetches, filtered
 *  client-side rather than duplicated as a new backend query, since this app's order
 *  volume is small enough that this is genuinely simpler than adding server-side
 *  filtering for no real benefit), and their running notes. Round 5 review: the old
 *  Customers list was a dead end with no way to see a repeat customer's history or fix a
 *  typo'd phone number — this closes that gap. */
export default function CustomerDetailPage() {
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const { id } = useParams<{ id: string }>();
  const customerId = id!;

  const [customer, setCustomer] = useState<Customer | null>(null);
  const [orders, setOrders] = useState<OrderView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [c, allOrders] = await Promise.all([
        orderTrackerApi.customer(groupId, customerId),
        orderTrackerApi.orders(groupId),
      ]);
      setCustomer(c);
      setOrders(allOrders.filter((o) => o.customerId === customerId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load customer");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId, customerId]);

  const startEditing = () => {
    if (!customer) return;
    setDraft(toDraft(customer));
    setError(null);
    setEditing(true);
  };

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!draft || !draft.name.trim()) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await orderTrackerApi.updateCustomer(groupId, customerId, {
        name: draft.name.trim(),
        contactNumber: draft.contactNumber.trim() || null,
        email: draft.email.trim() || null,
        instagramHandle: draft.instagramHandle.trim() || null,
        acquisitionChannel: draft.acquisitionChannel,
        firstContactDate: draft.firstContactDate ? new Date(draft.firstContactDate + "T00:00:00Z").toISOString() : null,
        addresses: draft.addresses.filter((a) => a.address.trim()).map((a) => ({
          addressId: a.addressId, label: a.label.trim() || "Address", address: a.address.trim(), isDefault: a.isDefault,
        })),
        notes: draft.notes.trim() || null,
      });
      setCustomer(updated);
      setEditing(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save changes");
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <p className="muted">Loading…</p>;
  if (error && !customer) return <div className="error">{error}</div>;
  if (!customer) return <p className="empty">Customer not found.</p>;

  return (
    <div>
      <div className="toolbar">
        <Link to="/ordertracker/customers" className="linkbtn">
          ‹ Customers
        </Link>
      </div>
      <h1 className="page-title">{customer.name}</h1>
      {error && <div className="error">{error}</div>}

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="toolbar">
          <h2 style={{ margin: 0 }}>Contact details</h2>
          <span className="spacer" />
          {!editing && (
            <button type="button" onClick={startEditing}>
              Edit
            </button>
          )}
        </div>

        {editing && draft ? (
          <form onSubmit={save} style={{ marginTop: 12 }}>
            <div className="form-row">
              <label htmlFor="cd-name">Name</label>
              <input id="cd-name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} required />
            </div>
            <div className="form-grid">
              <div className="form-row">
                <label htmlFor="cd-contact">Contact number</label>
                <input id="cd-contact" value={draft.contactNumber} onChange={(e) => setDraft({ ...draft, contactNumber: e.target.value })} />
              </div>
              <div className="form-row">
                <label htmlFor="cd-channel">Acquisition channel</label>
                <select id="cd-channel" value={draft.acquisitionChannel} onChange={(e) => setDraft({ ...draft, acquisitionChannel: e.target.value as AcquisitionChannel })}>
                  {CHANNELS.map((c) => (
                    <option key={c} value={c}>
                      {c.replace(/_/g, " ")}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="form-grid">
              <div className="form-row">
                <label htmlFor="cd-email">Email</label>
                <input id="cd-email" type="email" value={draft.email} onChange={(e) => setDraft({ ...draft, email: e.target.value })} />
              </div>
              <div className="form-row">
                <label htmlFor="cd-instagram">Instagram handle</label>
                <input id="cd-instagram" value={draft.instagramHandle} onChange={(e) => setDraft({ ...draft, instagramHandle: e.target.value })} />
              </div>
            </div>
            <div className="form-row">
              <label htmlFor="cd-first-contact">First contact date</label>
              <input id="cd-first-contact" type="date" value={draft.firstContactDate} onChange={(e) => setDraft({ ...draft, firstContactDate: e.target.value })} />
            </div>
            <div className="form-row">
              <label>Saved addresses</label>
              {draft.addresses.length === 0 && <p className="hint" style={{ marginTop: 0 }}>No addresses saved yet.</p>}
              {draft.addresses.map((a, i) => (
                <div key={a.addressId ?? `new-${i}`} className="card" style={{ background: "var(--bg-elev-2)", marginBottom: 8 }}>
                  <div className="form-grid">
                    <div className="form-row">
                      <label htmlFor={`cd-address-${i}-label`}>Label</label>
                      <input
                        id={`cd-address-${i}-label`}
                        placeholder="e.g. Home, Work"
                        value={a.label}
                        onChange={(e) => setDraft({
                          ...draft, addresses: draft.addresses.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)),
                        })}
                      />
                    </div>
                    <div className="form-row">
                      <label htmlFor={`cd-address-${i}-text`}>Address</label>
                      <textarea
                        id={`cd-address-${i}-text`}
                        value={a.address}
                        onChange={(e) => setDraft({
                          ...draft, addresses: draft.addresses.map((x, j) => (j === i ? { ...x, address: e.target.value } : x)),
                        })}
                      />
                    </div>
                  </div>
                  <div className="toolbar">
                    {a.isDefault ? (
                      <span className="badge">Default</span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setDraft({
                          ...draft, addresses: draft.addresses.map((x, j) => ({ ...x, isDefault: j === i })),
                        })}
                      >
                        Set as default
                      </button>
                    )}
                    <span className="spacer" />
                    <button
                      type="button"
                      aria-label={`Remove address ${a.label || i + 1}`}
                      onClick={() => setDraft({ ...draft, addresses: draft.addresses.filter((_, j) => j !== i) })}
                    >
                      Remove
                    </button>
                  </div>
                </div>
              ))}
              <button
                type="button"
                onClick={() => setDraft({ ...draft, addresses: [...draft.addresses, blankAddressDraft()] })}
              >
                + Add address
              </button>
            </div>
            <div className="form-row">
              <label htmlFor="cd-notes">Notes</label>
              <textarea
                id="cd-notes"
                placeholder="e.g. prefers pastel colours, allergic to wool"
                value={draft.notes}
                onChange={(e) => setDraft({ ...draft, notes: e.target.value })}
              />
            </div>
            <div className="toolbar">
              <button className="primary" type="submit" disabled={saving}>
                {saving ? "Saving…" : "Save changes"}
              </button>
              <button type="button" onClick={() => setEditing(false)} disabled={saving}>
                Cancel
              </button>
            </div>
          </form>
        ) : (
          <div className="kv" style={{ marginTop: 8 }}>
            <div className="row"><span className="k">Contact number</span><span className="v">{customer.contactNumber || <span className="muted">Not recorded</span>}</span></div>
            <div className="row"><span className="k">Email</span><span className="v">{customer.email || <span className="muted">Not recorded</span>}</span></div>
            <div className="row"><span className="k">Instagram</span><span className="v">{customer.instagramHandle || <span className="muted">Not recorded</span>}</span></div>
            <div className="row"><span className="k">Channel</span><span className="v"><span className="badge">{customer.acquisitionChannel.replace(/_/g, " ")}</span></span></div>
            <div className="row"><span className="k">First contact</span><span className="v">{formatDate(customer.firstContactDate)}</span></div>
            <div className="row" style={{ alignItems: "flex-start" }}>
              <span className="k">Addresses</span>
              <span className="v">
                {customer.addresses.length === 0 ? (
                  <span className="muted">Not recorded</span>
                ) : (
                  <div className="divided-list">
                    {customer.addresses.map((a) => (
                      <div key={a.addressId}>
                        <strong>{a.label}</strong>{a.isDefault && <span className="badge" style={{ marginLeft: 6 }}>Default</span>}
                        <div className="muted" style={{ whiteSpace: "pre-wrap" }}>{a.address}</div>
                      </div>
                    ))}
                  </div>
                )}
              </span>
            </div>
            <div className="row"><span className="k">Notes</span><span className="v">{customer.notes || <span className="muted">Nothing noted.</span>}</span></div>
          </div>
        )}
      </div>

      <div className="card">
        <h2>Orders ({orders.length})</h2>
        {orders.length === 0 ? (
          <p className="empty">No orders from this customer yet.</p>
        ) : (
          <div className="table-wrap">
            <table className="ot-table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Order #</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Completion</th>
                  <th>Due (est.)</th>
                  <th>Payment</th>
                </tr>
              </thead>
              <tbody>
                {orders.map((o) => (
                  <tr key={o.id}>
                    <td className="cell-title">
                      <Link to={`/ordertracker/orders/${o.id}`}>{o.itemName || <span className="muted">Untitled order</span>}</Link>
                    </td>
                    <td className="cell-order mono">{o.orderNumber}</td>
                    <td className="cell-type">
                      <span className="badge">{o.orderType}</span>
                    </td>
                    <td className="cell-status">
                      <span className="badge">{o.status.replace(/_/g, " ")}</span>
                    </td>
                    <td className="cell-completion">{o.completionPercentage.toFixed(0)}%</td>
                    <td className="cell-due">
                      <OrderDueDate iso={o.costEstimate?.computedDueDate ?? o.bulkDetails?.computedDueDate ?? null} status={o.status} />
                    </td>
                    <td className="cell-payment">
                      <span className="badge">{o.paymentStatus.replace(/_/g, " ")}</span>
                    </td>
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
