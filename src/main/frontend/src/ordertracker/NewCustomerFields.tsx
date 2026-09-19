import { useState } from "react";
import { orderTrackerApi } from "./api";
import type { AcquisitionChannel, Customer } from "./types";

const CHANNELS: AcquisitionChannel[] = ["INSTAGRAM", "WHATSAPP", "REFERRAL", "WORD_OF_MOUTH", "WALK_IN", "OTHER"];

/** The fields every "add a new customer" form needs beyond the name — contact/channel/
 *  email/instagram — plus the blind-index existing-customer lookup, shared between the
 *  standalone Customers page and the inline "new customer" branch of the new-order form
 *  (previously duplicated in both, including the async search-on-blur logic). */
export interface NewCustomerFieldsDraft {
  contactNumber: string;
  email: string;
  instagramHandle: string;
  acquisitionChannel: AcquisitionChannel;
}

export function blankNewCustomerFieldsDraft(): NewCustomerFieldsDraft {
  return { contactNumber: "", email: "", instagramHandle: "", acquisitionChannel: "INSTAGRAM" };
}

export function NewCustomerFields({
  groupId,
  draft,
  onChange,
  onUseExisting,
  useExistingLabel = "Use this customer's details",
  idPrefix,
}: {
  groupId: string;
  draft: NewCustomerFieldsDraft;
  onChange: (next: NewCustomerFieldsDraft) => void;
  onUseExisting: (customer: Customer) => void;
  useExistingLabel?: string;
  idPrefix: string;
}) {
  const [existingMatch, setExistingMatch] = useState<Customer | null>(null);

  const set = <K extends keyof NewCustomerFieldsDraft>(key: K, value: NewCustomerFieldsDraft[K]) =>
    onChange({ ...draft, [key]: value });

  const checkForExisting = async () => {
    if (!draft.email.trim() && !draft.instagramHandle.trim()) {
      setExistingMatch(null);
      return;
    }
    try {
      const matches = await orderTrackerApi.searchCustomers(groupId, {
        email: draft.email.trim() || undefined,
        instagramHandle: draft.instagramHandle.trim() || undefined,
      });
      setExistingMatch(matches[0] ?? null);
    } catch {
      setExistingMatch(null);
    }
  };

  return (
    <>
      <div className="form-grid">
        <div className="form-row">
          <label htmlFor={`${idPrefix}-contact`}>Contact number</label>
          <input id={`${idPrefix}-contact`} value={draft.contactNumber} onChange={(e) => set("contactNumber", e.target.value)} />
        </div>
        <div className="form-row">
          <label htmlFor={`${idPrefix}-channel`}>Acquisition channel</label>
          <select
            id={`${idPrefix}-channel`}
            value={draft.acquisitionChannel}
            onChange={(e) => set("acquisitionChannel", e.target.value as AcquisitionChannel)}
          >
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
          <label htmlFor={`${idPrefix}-email`}>Email</label>
          <input id={`${idPrefix}-email`} type="email" value={draft.email} onChange={(e) => set("email", e.target.value)} onBlur={checkForExisting} />
        </div>
        <div className="form-row">
          <label htmlFor={`${idPrefix}-instagram`}>Instagram handle</label>
          <input id={`${idPrefix}-instagram`} value={draft.instagramHandle} onChange={(e) => set("instagramHandle", e.target.value)} onBlur={checkForExisting} />
        </div>
      </div>
      {existingMatch && (
        <div className="hint" style={{ marginBottom: 12 }}>
          Found an existing customer: <strong>{existingMatch.name}</strong>.{" "}
          <button
            type="button"
            onClick={() => {
              onUseExisting(existingMatch);
              setExistingMatch(null);
            }}
          >
            {useExistingLabel}
          </button>
        </div>
      )}
    </>
  );
}
