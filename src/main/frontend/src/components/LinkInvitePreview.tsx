import type { LinkPreview } from "../lib/useLinkInvitePreview";

/** mb-23: renders one {@link LinkPreview} as two checkable name lists plus Confirm/Cancel —
 *  the shared UI half of {@code useLinkInvitePreview}, so Connections and every applet's
 *  own Manage page show the exact same review step. */
export function LinkInvitePreviewList({
  preview,
  targetName,
  busy,
  onToggle,
  onConfirm,
  onCancel,
}: {
  preview: LinkPreview;
  targetName: string;
  busy: boolean;
  onToggle: (personId: string) => void;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const nothingToInvite = preview.intoCurrent.length === 0 && preview.intoTarget.length === 0;
  return (
    <div>
      {nothingToInvite ? (
        <p className="muted" style={{ fontSize: 12 }}>No new invites suggested — everyone's already in both groups.</p>
      ) : (
        <>
          <p className="muted" style={{ fontSize: 12, margin: "0 0 4px" }}>
            Suggested invites — uncheck anyone you don't want invited:
          </p>
          {preview.intoCurrent.length > 0 && (
            <div style={{ marginBottom: 4 }}>
              <span className="muted" style={{ fontSize: 11 }}>Into this group</span>
              {preview.intoCurrent.map((m) => (
                <label key={m.id} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12 }}>
                  <input type="checkbox" checked={!!preview.checked[m.id]} onChange={() => onToggle(m.id)} />
                  {m.name}
                </label>
              ))}
            </div>
          )}
          {preview.intoTarget.length > 0 && (
            <div style={{ marginBottom: 4 }}>
              <span className="muted" style={{ fontSize: 11 }}>Into {targetName}</span>
              {preview.intoTarget.map((m) => (
                <label key={m.id} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12 }}>
                  <input type="checkbox" checked={!!preview.checked[m.id]} onChange={() => onToggle(m.id)} />
                  {m.name}
                </label>
              ))}
            </div>
          )}
        </>
      )}
      <div style={{ display: "flex", gap: 6, marginTop: 4 }}>
        <button type="button" className="primary" disabled={busy} onClick={onConfirm} style={{ fontSize: 12, padding: "2px 8px" }}>
          Confirm link
        </button>
        <button type="button" disabled={busy} onClick={onCancel} style={{ fontSize: 12, padding: "2px 8px" }}>
          Cancel
        </button>
      </div>
    </div>
  );
}
