import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/client";
import { groupLinkApi } from "../api/groupLinks";
import { otherApplets, type AppletMeta } from "../api/applets";
import { useAuth } from "../auth/AuthContext";
import { LinkInvitePreviewList } from "./LinkInvitePreview";
import { PendingLinkProposal } from "./PendingLinkProposal";
import { missingFrom, type LinkPreview } from "../lib/useLinkInvitePreview";
import { useDismissableMenu } from "../lib/useDismissableMenu";
import { usePopoverPosition } from "../lib/usePopoverPosition";
import type { GroupLinkProposalView, GroupView } from "../types";

interface RowState {
  linkedGroupId: string | null | undefined; // undefined = still loading
  groups: GroupView[] | undefined; // this applet's groups the current user belongs to
  selected: string;
  expanded: boolean;
  busy: boolean;
  error: string | null;
  preview: LinkPreview | null;
}

const blankRow = (): RowState => ({
  linkedGroupId: undefined, groups: undefined, selected: "", expanded: false, busy: false, error: null, preview: null,
});

/** One consistent "🔗 Connections" affordance in every applet's header, replacing the
 *  need to go hunting through each applet's own Manage/Settings page to find out (or set
 *  up) how it's linked to the others — every applet works standalone by design, but
 *  linking two together (an Order Tracker business to its Finance group, say) should be
 *  discoverable from anywhere, not just from the one page that happens to mention it
 *  (round 5 review). Shows every other applet's link status at a glance and lets you
 *  link/unlink right here. */
export default function Connections({ appletKey, groupId }: { appletKey: string; groupId: string | null }) {
  const { open, setOpen, ref } = useDismissableMenu<HTMLDivElement>();
  const triggerRef = useRef<HTMLButtonElement>(null);
  const { popoverRef, style: popoverStyle } = usePopoverPosition(triggerRef, open);
  const [rows, setRows] = useState<Record<string, RowState>>({});
  const [currentGroup, setCurrentGroup] = useState<GroupView | undefined>(undefined);
  const [pending, setPending] = useState<GroupLinkProposalView | null>(null);
  const [pendingBusy, setPendingBusy] = useState(false);
  const { user } = useAuth();
  const nav = useNavigate();
  const others = otherApplets(appletKey);

  const load = () => {
    if (!groupId) return;
    const initial: Record<string, RowState> = {};
    others.forEach((a) => (initial[a.key] = blankRow()));
    setRows(initial);
    groupLinkApi.group(groupId).then(setCurrentGroup).catch(() => setCurrentGroup(undefined));
    groupLinkApi.linkProposals(groupId).then((proposals) => setPending(proposals.find((p) => p.status === "PENDING") ?? null));
    others.forEach((a) => {
      Promise.all([groupLinkApi.linkedGroupId(groupId, a.key), groupLinkApi.myGroupsIn(a.key)])
        .then(([linkedGroupId, groups]) => {
          setRows((prev) => ({ ...prev, [a.key]: { ...prev[a.key], linkedGroupId, groups } }));
        })
        .catch(() => {
          setRows((prev) => ({ ...prev, [a.key]: { ...prev[a.key], linkedGroupId: null, groups: [], error: "Couldn't load" } }));
        });
    });
  };

  /** Resolves the pending proposal's target group name once that applet's row has loaded
   *  its candidate groups (the proposal itself only carries the id). */
  const pendingTargetName = () => {
    if (!pending) return "another group";
    for (const a of others) {
      const match = rows[a.key]?.groups?.find((g) => g.id === pending.targetGroupId);
      if (match) return match.name;
    }
    return "another group";
  };

  const respondToPending = async (approve: boolean) => {
    if (!groupId || !pending) return;
    setPendingBusy(true);
    try {
      const updated = approve
        ? await groupLinkApi.approveLinkProposal(groupId, pending.id)
        : await groupLinkApi.rejectLinkProposal(groupId, pending.id);
      setPending(updated.status === "PENDING" ? updated : null);
      if (updated.status === "APPROVED") {
        load();
      }
    } finally {
      setPendingBusy(false);
    }
  };

  const toggleOpen = () => {
    if (!open) load();
    setOpen((o) => !o);
  };

  const patchRow = (key: string, patch: Partial<RowState>) =>
    setRows((prev) => ({ ...prev, [key]: { ...prev[key], ...patch } }));

  /** mb-23: shows the suggested two-way invite delta instead of linking immediately —
   *  every business member not yet in the target group, and every target-group member not
   *  yet in the business, all pre-checked but freely uncheckable before anything sends. */
  const reviewLink = (a: AppletMeta) => {
    const row = rows[a.key];
    const target = row?.groups?.find((g) => g.id === row.selected);
    if (!row || !target || !currentGroup) return;
    const intoCurrent = missingFrom(target.members, currentGroup.members);
    const intoTarget = missingFrom(currentGroup.members, target.members);
    const checked: Record<string, boolean> = {};
    [...intoCurrent, ...intoTarget].forEach((m) => (checked[m.id] = true));
    patchRow(a.key, { preview: { intoCurrent, intoTarget, checked }, error: null });
  };

  const toggleChecked = (a: AppletMeta, personId: string) => {
    const preview = rows[a.key]?.preview;
    if (!preview) return;
    patchRow(a.key, { preview: { ...preview, checked: { ...preview.checked, [personId]: !preview.checked[personId] } } });
  };

  /** mb-14: proposes the link — gated behind unanimous approval from this group's own
   *  members, same as every other approval-backed flow. A solo-member group still resolves
   *  immediately. */
  const confirmLink = async (a: AppletMeta) => {
    const row = rows[a.key];
    const preview = row?.preview;
    if (!groupId || !row?.selected || !preview) return;
    patchRow(a.key, { busy: true, error: null });
    try {
      const intoCurrentEmails = preview.intoCurrent.filter((m) => preview.checked[m.id]).map((m) => m.email);
      const intoTargetEmails = preview.intoTarget.filter((m) => preview.checked[m.id]).map((m) => m.email);
      const proposal = await groupLinkApi.proposeLink(groupId, row.selected, intoCurrentEmails, intoTargetEmails);
      if (proposal.status === "APPROVED") {
        patchRow(a.key, { linkedGroupId: row.selected, expanded: false, busy: false, preview: null });
      } else {
        patchRow(a.key, { expanded: false, busy: false, preview: null });
        setPending(proposal);
      }
    } catch (e) {
      patchRow(a.key, { busy: false, error: e instanceof ApiError ? e.message : "Could not propose the link" });
    }
  };

  const doUnlink = async (a: AppletMeta) => {
    if (!groupId) return;
    patchRow(a.key, { busy: true, error: null });
    try {
      await groupLinkApi.unlink(groupId, a.key);
      patchRow(a.key, { linkedGroupId: null, selected: "", busy: false });
    } catch (e) {
      patchRow(a.key, { busy: false, error: e instanceof ApiError ? e.message : "Could not unlink" });
    }
  };

  if (!groupId) return null;

  return (
    <div className="navmenu" ref={ref}>
      <button
        ref={triggerRef}
        type="button"
        className="iconbtn"
        title="Connections to other applets"
        aria-label="Connections to other applets"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={toggleOpen}
      >
        🔗
      </button>
      {open && (
        <div className="navmenu-pop" role="menu" ref={popoverRef} style={{ ...popoverStyle, width: 300 }}>
          <div className="navmenu-head">Connections</div>
          <p className="muted" style={{ fontSize: 12, padding: "0 10px 8px" }}>
            Every applet works on its own — link this group to another one only if you want
            its data to show up here too.
          </p>
          {pending && (
            <div style={{ padding: "8px 10px", borderTop: "1px solid var(--border-soft)" }}>
              <PendingLinkProposal
                proposal={pending}
                targetName={pendingTargetName()}
                haveApproved={pending.approvedByUserIds.includes(user?.id ?? "")}
                busy={pendingBusy}
                onApprove={() => respondToPending(true)}
                onReject={() => respondToPending(false)}
              />
            </div>
          )}
          {others.map((a) => {
            const row = rows[a.key] ?? blankRow();
            const linkedGroup = row.groups?.find((g) => g.id === row.linkedGroupId);
            return (
              <div key={a.key} style={{ padding: "8px 10px", borderTop: "1px solid var(--border-soft)" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 4 }}>
                  <span>{a.icon}</span>
                  <button
                    type="button"
                    className="linkbtn"
                    style={{ fontWeight: 600 }}
                    onClick={() => {
                      setOpen(false);
                      nav(a.href);
                    }}
                  >
                    {a.name}
                  </button>
                </div>
                {row.linkedGroupId === undefined ? (
                  <p className="muted" style={{ fontSize: 12, margin: 0 }}>Loading…</p>
                ) : row.linkedGroupId ? (
                  <div className="row" style={{ alignItems: "center" }}>
                    <span className="muted" style={{ fontSize: 12 }}>
                      Linked to <strong>{linkedGroup?.name ?? "a group"}</strong>
                    </span>
                    <button type="button" disabled={row.busy} onClick={() => doUnlink(a)} style={{ fontSize: 12, padding: "2px 8px" }}>
                      Unlink
                    </button>
                  </div>
                ) : pending ? (
                  <p className="muted" style={{ fontSize: 12, margin: 0 }}>A link proposal is already pending for this group.</p>
                ) : !row.expanded ? (
                  <button
                    type="button"
                    className="linkbtn"
                    style={{ fontSize: 12 }}
                    onClick={() => patchRow(a.key, { expanded: true })}
                    disabled={(row.groups ?? []).length === 0}
                  >
                    {(row.groups ?? []).length === 0 ? `No ${a.name} groups yet` : "Link…"}
                  </button>
                ) : row.preview ? (
                  <LinkInvitePreviewList
                    preview={row.preview}
                    targetName={a.name}
                    busy={row.busy}
                    onToggle={(id) => toggleChecked(a, id)}
                    onConfirm={() => confirmLink(a)}
                    onCancel={() => patchRow(a.key, { preview: null })}
                  />
                ) : (
                  <div style={{ display: "flex", gap: 6 }}>
                    <select
                      aria-label={`Group to link in ${a.name}`}
                      value={row.selected}
                      onChange={(e) => patchRow(a.key, { selected: e.target.value })}
                      style={{ flex: 1, fontSize: 12 }}
                    >
                      <option value="" disabled>
                        Select…
                      </option>
                      {(row.groups ?? []).map((g) => (
                        <option key={g.id} value={g.id}>
                          {g.name}
                        </option>
                      ))}
                    </select>
                    <button type="button" className="primary" disabled={row.busy || !row.selected} onClick={() => reviewLink(a)} style={{ fontSize: 12, padding: "2px 8px" }}>
                      Review &amp; link
                    </button>
                  </div>
                )}
                {row.error && <div className="error" style={{ fontSize: 12, marginTop: 4 }}>{row.error}</div>}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
