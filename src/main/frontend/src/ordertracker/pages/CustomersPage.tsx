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
  const [channel, setChannel] = useState<AcquisitionChannel>("INSTAGRAM");
  const [error, setError] = useState<string | null>(null);

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

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!name.trim()) return;
    try {
      await orderTrackerApi.createCustomer(groupId, { name: name.trim(), contactNumber, acquisitionChannel: channel });
      setName("");
      setContactNumber("");
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
            <label>Name</label>
            <input value={name} onChange={(e) => setName(e.target.value)} required />
          </div>
          <div className="form-row">
            <label>Contact number</label>
            <input value={contactNumber} onChange={(e) => setContactNumber(e.target.value)} />
          </div>
          <div className="form-row">
            <label>Acquisition channel</label>
            <select value={channel} onChange={(e) => setChannel(e.target.value as AcquisitionChannel)}>
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
