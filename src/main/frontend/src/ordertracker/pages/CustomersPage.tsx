import { useEffect, useState } from "react";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import type { AcquisitionChannel, Customer } from "../types";

const CHANNELS: AcquisitionChannel[] = ["INSTAGRAM", "WHATSAPP", "REFERRAL", "WORD_OF_MOUTH", "WALK_IN", "OTHER"];

export default function CustomersPage() {
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [contactNumber, setContactNumber] = useState("");
  const [email, setEmail] = useState("");
  const [instagramHandle, setInstagramHandle] = useState("");
  const [channel, setChannel] = useState<AcquisitionChannel>("INSTAGRAM");
  const [error, setError] = useState<string | null>(null);
  const [existingMatch, setExistingMatch] = useState<Customer | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      setCustomers(await orderTrackerApi.customers(groupId));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId]);

  const checkForExisting = async () => {
    if (!email.trim() && !instagramHandle.trim()) {
      setExistingMatch(null);
      return;
    }
    try {
      const matches = await orderTrackerApi.searchCustomers(groupId, {
        email: email.trim() || undefined,
        instagramHandle: instagramHandle.trim() || undefined,
      });
      setExistingMatch(matches[0] ?? null);
    } catch {
      setExistingMatch(null);
    }
  };

  const useExistingMatch = () => {
    if (!existingMatch) return;
    setName(existingMatch.name);
    setContactNumber(existingMatch.contactNumber ?? "");
    setEmail(existingMatch.email ?? "");
    setInstagramHandle(existingMatch.instagramHandle ?? "");
    setChannel(existingMatch.acquisitionChannel);
    setExistingMatch(null);
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!name.trim()) return;
    try {
      await orderTrackerApi.createCustomer(groupId, {
        name: name.trim(),
        contactNumber,
        email: email.trim() || null,
        instagramHandle: instagramHandle.trim() || null,
        acquisitionChannel: channel,
      });
      setName("");
      setContactNumber("");
      setEmail("");
      setInstagramHandle("");
      setExistingMatch(null);
      setShowForm(false);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add customer");
    }
  };

  return (
    <div>
      <div className="toolbar">
        <h1 className="page-title" style={{ marginBottom: 0 }}>
          Customers
        </h1>
        <span className="spacer" />
        <button className="primary" onClick={() => setShowForm((s) => !s)}>
          {showForm ? "Close" : "+ Add customer"}
        </button>
      </div>

      {showForm && (
        <form className="card" onSubmit={submit} style={{ marginBottom: 16 }}>
          {error && <div className="error">{error}</div>}
          <div className="form-row">
            <label htmlFor="cust-name">Name</label>
            <input id="cust-name" value={name} onChange={(e) => setName(e.target.value)} required />
          </div>
          <div className="form-row">
            <label htmlFor="cust-contact">Contact number</label>
            <input id="cust-contact" value={contactNumber} onChange={(e) => setContactNumber(e.target.value)} />
          </div>
          <div className="form-grid">
            <div className="form-row">
              <label htmlFor="cust-email">Email</label>
              <input id="cust-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} onBlur={checkForExisting} />
            </div>
            <div className="form-row">
              <label htmlFor="cust-instagram">Instagram handle</label>
              <input id="cust-instagram" value={instagramHandle} onChange={(e) => setInstagramHandle(e.target.value)} onBlur={checkForExisting} />
            </div>
          </div>
          {existingMatch && (
            <div className="hint" style={{ marginBottom: 12 }}>
              Found an existing customer: <strong>{existingMatch.name}</strong>.{" "}
              <button type="button" onClick={useExistingMatch}>
                Use this customer's details
              </button>
            </div>
          )}
          <div className="form-row">
            <label htmlFor="cust-channel">Acquisition channel</label>
            <select id="cust-channel" value={channel} onChange={(e) => setChannel(e.target.value as AcquisitionChannel)}>
              {CHANNELS.map((c) => (
                <option key={c} value={c}>
                  {c.replace(/_/g, " ")}
                </option>
              ))}
            </select>
          </div>
          <button className="primary" type="submit">
            Save customer
          </button>
        </form>
      )}

      {loading ? (
        <p className="muted">Loading…</p>
      ) : customers.length === 0 ? (
        <p className="empty">No customers yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Contact</th>
                <th>Channel</th>
              </tr>
            </thead>
            <tbody>
              {customers.map((c) => (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td>{c.contactNumber || <span className="muted">—</span>}</td>
                  <td>
                    <span className="badge">{c.acquisitionChannel.replace(/_/g, " ")}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
