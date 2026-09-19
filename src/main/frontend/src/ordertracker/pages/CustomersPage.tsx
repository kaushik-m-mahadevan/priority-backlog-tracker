import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AsyncSection } from "../../components/AsyncSection";
import { orderTrackerApi } from "../api";
import { useBusiness } from "../BusinessContext";
import { NewCustomerFields, blankNewCustomerFieldsDraft, type NewCustomerFieldsDraft } from "../NewCustomerFields";
import type { Customer } from "../types";

export default function CustomersPage() {
  const { currentGroupId } = useBusiness();
  const groupId = currentGroupId!;
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [draft, setDraft] = useState<NewCustomerFieldsDraft>(blankNewCustomerFieldsDraft());
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

  const useExistingMatch = (match: Customer) => {
    setName(match.name);
    setDraft({
      contactNumber: match.contactNumber ?? "",
      email: match.email ?? "",
      instagramHandle: match.instagramHandle ?? "",
      acquisitionChannel: match.acquisitionChannel,
    });
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!name.trim()) return;
    try {
      await orderTrackerApi.createCustomer(groupId, {
        name: name.trim(),
        contactNumber: draft.contactNumber,
        email: draft.email.trim() || null,
        instagramHandle: draft.instagramHandle.trim() || null,
        acquisitionChannel: draft.acquisitionChannel,
      });
      setName("");
      setDraft(blankNewCustomerFieldsDraft());
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
          <NewCustomerFields
            groupId={groupId}
            draft={draft}
            onChange={setDraft}
            onUseExisting={useExistingMatch}
            idPrefix="cust"
          />
          <button className="primary" type="submit">
            Save customer
          </button>
        </form>
      )}

      <AsyncSection loading={loading} isEmpty={customers.length === 0} empty={<p className="empty">No customers yet.</p>}>
        <div className="table-wrap">
          <table className="ot-table">
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
                  <td className="cell-title">
                    <Link to={`/ordertracker/customers/${c.id}`}>{c.name}</Link>
                  </td>
                  <td className="cell-subtitle">{c.contactNumber || <span className="muted">No contact number</span>}</td>
                  <td className="cell-type">
                    <span className="badge">{c.acquisitionChannel.replace(/_/g, " ")}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </AsyncSection>
    </div>
  );
}
