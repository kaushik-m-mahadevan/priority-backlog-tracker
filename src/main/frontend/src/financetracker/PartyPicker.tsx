import type { ExternalPartySuggestion, PartyInput, PartyType, PartyView } from "./types";

export type PartyDraft = { type: PartyType; userId: string; displayName: string };

export const blankPartyDraft = (defaultUserId = ""): PartyDraft => ({ type: "MEMBER", userId: defaultUserId, displayName: "" });

export function partyDraftFromView(p: PartyView): PartyDraft {
  return { type: p.type, userId: p.userId ?? "", displayName: p.displayName ?? "" };
}

export function toPartyInput(draft: PartyDraft): PartyInput | null {
  if (draft.type === "MEMBER") {
    return draft.userId ? { type: "MEMBER", userId: draft.userId, displayName: null } : null;
  }
  if (draft.type === "BUSINESS") {
    return { type: "BUSINESS", userId: null, displayName: null };
  }
  return draft.displayName.trim() ? { type: draft.type, userId: null, displayName: draft.displayName.trim() } : null;
}

/** The Debit/Credit party picker used on both the ledger's create form and the entry
 *  detail/edit modal — extracted once a second real caller needed the identical
 *  type-select + conditional member/name sub-field logic (established pattern in this
 *  codebase, e.g. mb-4/mb-18's own shared-hook extractions). */
export function PartyPicker({
  draft,
  setDraft,
  idPrefix,
  label,
  members,
  currentUserId,
  businessAccountEnabled,
  suggestions,
}: {
  draft: PartyDraft;
  setDraft: (d: PartyDraft) => void;
  idPrefix: string;
  label: string;
  members: { id: string; name: string }[];
  currentUserId: string | undefined;
  businessAccountEnabled: boolean;
  suggestions: ExternalPartySuggestion[];
}) {
  return (
    <div className="form-row">
      <label htmlFor={`${idPrefix}-type`}>{label}</label>
      <select
        id={`${idPrefix}-type`}
        value={draft.type}
        onChange={(e) => setDraft({ ...draft, type: e.target.value as PartyType })}
      >
        <option value="MEMBER">A member</option>
        {(businessAccountEnabled || draft.type === "BUSINESS") && <option value="BUSINESS">Business Account</option>}
        <option value="CUSTOMER">Customer (name)</option>
        <option value="EXTERNAL">Someone else (name)</option>
      </select>
      {draft.type === "MEMBER" && (
        <select
          aria-label={`${idPrefix} member`}
          value={draft.userId}
          onChange={(e) => setDraft({ ...draft, userId: e.target.value })}
          style={{ marginTop: 6 }}
        >
          <option value="">Select…</option>
          {members.map((m) => (
            <option key={m.id} value={m.id}>
              {m.id === currentUserId ? "You" : m.name}
            </option>
          ))}
        </select>
      )}
      {(draft.type === "CUSTOMER" || draft.type === "EXTERNAL") && (
        <>
          <input
            aria-label={`${idPrefix} name`}
            placeholder="Name"
            value={draft.displayName}
            onChange={(e) => setDraft({ ...draft, displayName: e.target.value })}
            style={{ marginTop: 6 }}
            list={draft.type === "EXTERNAL" ? `${idPrefix}-external-suggestions` : undefined}
          />
          {draft.type === "EXTERNAL" && (
            <datalist id={`${idPrefix}-external-suggestions`}>
              {suggestions.map((s) => (
                <option key={s.displayName} value={s.displayName} />
              ))}
            </datalist>
          )}
        </>
      )}
    </div>
  );
}
