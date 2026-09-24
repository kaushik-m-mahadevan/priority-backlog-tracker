import { FormEvent, useEffect, useState } from "react";
import { api, ApiError } from "../../api/client";
import { Creature } from "../../components/Creature";
import { LinkInvitePreviewList } from "../../components/LinkInvitePreview";
import { PendingLinkProposal } from "../../components/PendingLinkProposal";
import { Switch } from "../../components/Switch";
import { useAuth } from "../../auth/AuthContext";
import { useLinkInvitePreview } from "../../lib/useLinkInvitePreview";
import { financeTrackerApi } from "../api";
import { useFinanceGroup } from "../FinanceGroupContext";
import type { GroupView } from "../../types";

const ORDER_TRACKER_APPLET_KEY = "ordertracker";

/** Finance Tracker's equivalent of Order Tracker's "Manage business" page — invite
 *  members and link/unlink the business whose payments this group tracks. Same
 *  underlying commons endpoints (a finance group is just a Group scoped to the
 *  financetracker appletKey), mirrored here since Finance Tracker didn't have any
 *  member-management or business-linking UI at all before this. */
export default function ManageFinanceGroupPage() {
  const { currentFinanceGroup, currentGroupId, refresh } = useFinanceGroup();
  const { user } = useAuth();
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
  const [businessAccountConfigured, setBusinessAccountConfigured] = useState(false);
  const [backfillResult, setBackfillResult] = useState<string | null>(null);
  const linkPreview = useLinkInvitePreview(currentGroupId);

  useEffect(() => {
    if (!currentGroupId) return;
    setLinkLoading(true);
    Promise.all([
      api.get<{ linkedGroupId: string | null }>(`/groups/${currentGroupId}/links/${ORDER_TRACKER_APPLET_KEY}`),
      api.get<GroupView[]>(`/groups?appletKey=${ORDER_TRACKER_APPLET_KEY}`),
      financeTrackerApi.businessConfig(currentGroupId),
    ])
      .then(([link, groups, cfg]) => {
        setLinkedBusinessId(link.linkedGroupId);
        setBusinesses(groups);
        setBusinessAccountConfigured(cfg.businessAccountConfigured);
      })
      .finally(() => setLinkLoading(false));
  }, [currentGroupId]);

  useEffect(() => {
    linkPreview.loadPending();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentGroupId]);

  const toggleBusinessAccount = async (value: boolean) => {
    if (!currentGroupId) return;
    const cfg = await financeTrackerApi.setBusinessAccountConfigured(currentGroupId, value);
    setBusinessAccountConfigured(cfg.businessAccountConfigured);
  };

  const backfillPayments = async () => {
    if (!linkedBusinessId) return;
    setBusy(true);
    setErr(null);
    setBackfillResult(null);
    try {
      const result = await api.post<{ paymentsSynced: number }>(`/ordertracker/groups/${linkedBusinessId}/finance-sync/backfill`);
      setBackfillResult(`Synced ${result.paymentsSynced} historical payment(s).`);
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Could not sync historical payments");
    } finally {
      setBusy(false);
    }
  };

  const reviewLinkBusiness = () => {
    const target = businesses.find((g) => g.id === selectedBusinessId);
    if (!target || !currentFinanceGroup) return;
    linkPreview.review(currentFinanceGroup.members, target);
  };

  const confirmLinkBusiness = () =>
    linkPreview.confirm(
      selectedBusinessId,
      () => {
        setLinkedBusinessId(selectedBusinessId);
        setMsg("Business linked.");
      },
      () => setMsg("Link proposed — waiting for every other member to approve.")
    );

  const respondToPendingLink = async (approve: boolean) => {
    const updated = await linkPreview.respond(approve);
    if (updated?.status === "APPROVED") {
      setLinkedBusinessId(updated.targetGroupId);
      setMsg("Business linked.");
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

  if (!currentFinanceGroup || !currentGroupId) return <p className="muted">Loading…</p>;

  const linkedBusiness = businesses.find((g) => g.id === linkedBusinessId);

  const startRename = () => {
    setRenaming(true);
    setRenameValue(currentFinanceGroup.name);
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
      setMsg("Finance group renamed.");
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "Could not rename the finance group");
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
      <h1 className="page-title">Manage finance group</h1>
      <p className="page-sub">
        Everyone in {currentFinanceGroup.name} has the same rights — there's no owner.
      </p>
      {err && <div className="error">{err}</div>}
      {msg && (
        <div className="hint" style={{ color: "var(--growth)", marginBottom: 12 }}>
          {msg}
        </div>
      )}

      <div className="card" style={{ marginBottom: 16 }}>
        <h2>{currentFinanceGroup.name}</h2>
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
            <input aria-label="Finance group name" autoFocus value={renameValue} maxLength={60} onChange={(e) => setRenameValue(e.target.value)} />
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
          {currentFinanceGroup.members.map((m) => (
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
          Kept as its own separate workspace on purpose, so tracking payments doesn't have to share
          access with everyday order-taking. At most one business can be linked at a time.
        </p>
        {linkLoading ? (
          <p className="muted">Loading…</p>
        ) : linkedBusinessId ? (
          <>
            <div className="team-add">
              <span className="chip">{linkedBusiness?.name ?? "Linked business"}</span>
              <button className="ghost" disabled={busy} onClick={unlinkBusiness}>
                Unlink
              </button>
            </div>
            <div style={{ marginTop: 10 }}>
              <button type="button" disabled={busy} onClick={backfillPayments}>
                Sync historical payments
              </button>
              {backfillResult && <p className="hint" style={{ marginTop: 6 }}>{backfillResult}</p>}
            </div>
          </>
        ) : linkPreview.pending ? (
          <PendingLinkProposal
            proposal={linkPreview.pending}
            targetName={businesses.find((g) => g.id === linkPreview.pending!.targetGroupId)?.name ?? "the business"}
            haveApproved={linkPreview.pending.approvedByUserIds.includes(user?.id ?? "")}
            busy={linkPreview.busy}
            onApprove={() => respondToPendingLink(true)}
            onReject={() => respondToPendingLink(false)}
          />
        ) : businesses.length === 0 ? (
          <p className="empty">
            No Order Tracker businesses yet — create one from the Order Tracker applet first, then come
            back here to link it.
          </p>
        ) : linkPreview.preview ? (
          <LinkInvitePreviewList
            preview={linkPreview.preview}
            targetName={businesses.find((g) => g.id === selectedBusinessId)?.name ?? "the business"}
            busy={linkPreview.busy}
            onToggle={linkPreview.toggle}
            onConfirm={confirmLinkBusiness}
            onCancel={linkPreview.cancel}
          />
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
            <button className="primary" disabled={busy || !selectedBusinessId} onClick={reviewLinkBusiness}>
              Review &amp; link
            </button>
          </div>
        )}
        {linkPreview.error && <div className="error" style={{ marginTop: 8 }}>{linkPreview.error}</div>}
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2>Ledger settings</h2>
        <Switch
          id="business-account-configured"
          checked={businessAccountConfigured}
          onChange={toggleBusinessAccount}
          label="Business account configured"
          description="Lets a ledger entry credit or debit the business account itself, not just members."
        />
      </div>
    </div>
  );
}
