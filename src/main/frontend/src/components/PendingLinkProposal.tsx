import type { GroupLinkProposalView } from "../types";

/** mb-14: shows a group-link proposal awaiting this group's own unanimous approval, with
 *  inline Approve/Reject — same shape as every other approval-backed flow's pending card
 *  (cost-config, order finalization, profit split), kept tiny here since it lives inside
 *  the compact Connections popover as well as each applet's own Manage page. */
export function PendingLinkProposal({
  proposal,
  targetName,
  haveApproved,
  busy,
  onApprove,
  onReject,
}: {
  proposal: GroupLinkProposalView;
  targetName: string;
  haveApproved: boolean;
  busy: boolean;
  onApprove: () => void;
  onReject: () => void;
}) {
  return (
    <div>
      <p className="muted" style={{ fontSize: 12, margin: "0 0 4px" }}>
        Proposed linking to <strong>{targetName}</strong> — approved by {proposal.approvedByUserIds.length}/
        {proposal.groupMemberIds.length} member(s).
      </p>
      {haveApproved ? (
        <p className="hint" style={{ fontSize: 12 }}>You've approved this — waiting on everyone else.</p>
      ) : (
        <div style={{ display: "flex", gap: 6 }}>
          <button type="button" className="primary" disabled={busy} onClick={onApprove} style={{ fontSize: 12, padding: "2px 8px" }}>
            Approve
          </button>
          <button type="button" disabled={busy} onClick={onReject} style={{ fontSize: 12, padding: "2px 8px" }}>
            Reject
          </button>
        </div>
      )}
    </div>
  );
}
