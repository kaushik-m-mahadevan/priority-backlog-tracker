import { FormEvent, useEffect, useState } from "react";
import { api, ApiError } from "../../api/client";
import { Creature } from "../../components/Creature";
import { useProductCatalog } from "../ProductCatalogContext";
import type { GroupView } from "../../types";

const ORDER_TRACKER_APPLET_KEY = "ordertracker";

/** Product Catalog's equivalent of Order Tracker's "Manage business" / Finance Tracker's
 *  "Manage finance group" pages (mb-22) — Product Catalog had no invite or business-link
 *  UI at all before this, even though the backend's generic invite/link endpoints already
 *  supported it unchanged. Mirrors ManageFinanceGroupPage.tsx, minus its finance-specific
 *  "Ledger settings" card (business-account-configured, payment backfill) — nothing here
 *  has an equivalent for a colorway catalog. */
export default function ManageCatalogGroupPage() {
  const { currentCatalogGroup, currentGroupId, refresh } = useProductCatalog();
  const [invite, setInvite] = useState("");
  const [renaming, setRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const [linkedBusinessId, setLinkedBusinessId] = useState<string | null>(null);
  const [businesses, setBusinesses] = useState<GroupView[]>([]);
  const [selectedBusinessId, setSelectedBusinessId] = useState("");
  const [linkLoading, setLinkLoading] = useState(true);

  useEffect(() => {
    if (!currentGroupId) return;
    setLinkLoading(true);
    Promise.all([
      api.get<{ linkedGroupId: string | null }>(`/groups/${currentGroupId}/links/${ORDER_TRACKER_APPLET_KEY}`),
      api.get<GroupView[]>(`/groups?appletKey=${ORDER_TRACKER_APPLET_KEY}`),
    ])
      .then(([link, groups]) => {
        setLinkedBusinessId(link.linkedGroupId);
        setBusinesses(groups);
      })
      .finally(() => setLinkLoading(false));
  }, [currentGroupId]);

  const linkBusiness = async () => {
    if (!selectedBusinessId) return;
    setBusy(true);
    setErr(null);
    try {
      await api.post(`/groups/${currentGroupId}/links`, { groupId: selectedBusinessId });
      setLinkedBusinessId(selectedBusinessId);
      setMsg("Business linked.");
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Could not link that business");
    } finally {
      setBusy(false);
    }
  };

  const unlinkBusiness = async () => {
    setBusy(true);
    setErr(null);
    try {
      await api.delete(`/groups/${currentGroupId}/links/${ORDER_TRACKER_APPLET_KEY}`);
      setLinkedBusinessId(null);
      setSelectedBusinessId("");
      setMsg("Business unlinked.");
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Could not unlink the business");
    } finally {
      setBusy(false);
    }
  };

  if (!currentCatalogGroup || !currentGroupId) return <p className="muted">Loading…</p>;

  const linkedBusiness = businesses.find((g) => g.id === linkedBusinessId);

  const startRename = () => {
    setRenaming(true);
    setRenameValue(currentCatalogGroup.name);
    setErr(null);
    setMsg(null);
  };

  const saveRename = async () => {
    const next = renameValue.trim();
    if (!next) return;
    setBusy(true);
    setErr(null);
    try {
      await api.patch(`/groups/${currentGroupId}`, { name: next });
      setRenaming(false);
      await refresh();
      setMsg("Catalog group renamed.");
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Could not rename the catalog group");
    } finally {
      setBusy(false);
    }
  };

  const sendInvite = async (e: FormEvent) => {
    e.preventDefault();
    const to = invite.trim();
    if (!to) return;
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      await api.post(`/groups/${currentGroupId}/invites`, { to });
      setInvite("");
      setMsg(`Invite sent to ${to}.`);
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Could not send the invite");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1 className="page-title">Manage catalog group</h1>
      <p className="page-sub">
        Everyone in {currentCatalogGroup.name} has the same rights — there's no owner.
      </p>
      {err && <div className="error">{err}</div>}
      {msg && (
        <div className="hint" style={{ color: "var(--growth)", marginBottom: 12 }}>
          {msg}
        </div>
      )}

      <div className="card" style={{ marginBottom: 16 }}>
        <h2>{currentCatalogGroup.name}</h2>
        {renaming ? (
          <div
            className="team-add"
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                saveRename();
              } else if (e.key === "Escape") {
                setRenaming(false);
              }
            }}
          >
            <input aria-label="Catalog group name" autoFocus value={renameValue} maxLength={60} onChange={(e) => setRenameValue(e.target.value)} />
            <button className="primary" disabled={busy || !renameValue.trim()} onClick={saveRename}>
              Save
            </button>
            <button className="ghost" disabled={busy} onClick={() => setRenaming(false)}>
              Cancel
            </button>
          </div>
        ) : (
          <button className="linkbtn" disabled={busy} onClick={startRename}>
            Rename
          </button>
        )}

        <div className="kv" style={{ marginTop: 12 }}>
          {currentCatalogGroup.members.map((m) => (
            <span className="chip" key={m.id}>
              <Creature seed={m.id} label={m.name} size={18} />
              {m.name} <span className="muted">@{m.handle}</span>
            </span>
          ))}
        </div>

        <form className="team-add" style={{ marginTop: 12 }} onSubmit={sendInvite}>
          <input
            aria-label="Invite by email or handle"
            placeholder="Invite by email or @handle"
            value={invite}
            onChange={(e) => setInvite(e.target.value)}
          />
          <button className="primary" disabled={busy || !invite.trim()}>
            Invite
          </button>
        </form>
      </div>

      <div className="card">
        <h2>Business</h2>
        <p className="muted" style={{ marginTop: -4, marginBottom: 10 }}>
          Kept as its own separate workspace on purpose, so building out the catalog doesn't have to share
          access with everyday order-taking. At most one business can be linked at a time.
        </p>
        {linkLoading ? (
          <p className="muted">Loading…</p>
        ) : linkedBusinessId ? (
          <div className="team-add">
            <span className="chip">{linkedBusiness?.name ?? "Linked business"}</span>
            <button className="ghost" disabled={busy} onClick={unlinkBusiness}>
              Unlink
            </button>
          </div>
        ) : businesses.length === 0 ? (
          <p className="empty">
            No Order Tracker businesses yet — create one from the Order Tracker applet first, then come
            back here to link it.
          </p>
        ) : (
          <div className="team-add">
            <label htmlFor="link-business" className="sr-only">
              Business to link
            </label>
            <select
              id="link-business"
              value={selectedBusinessId}
              onChange={(e) => setSelectedBusinessId(e.target.value)}
            >
              <option value="" disabled>
                Select a business…
              </option>
              {businesses.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name}
                </option>
              ))}
            </select>
            <button className="primary" disabled={busy || !selectedBusinessId} onClick={linkBusiness}>
              Link
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
