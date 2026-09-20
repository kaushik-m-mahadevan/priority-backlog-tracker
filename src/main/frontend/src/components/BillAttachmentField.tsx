import FilePreview from "./FilePreview";
import ImageGallery from "./ImageGallery";

export const BILL_ACCEPT = "image/jpeg,image/png,image/webp,application/pdf";
export const LEDGER_ENTRY_OWNER_TYPE = "ledgerEntry";

/** The preview half of {@link BillSideBySide}'s two-column layout, shared by every form
 *  that attaches a bill/invoice to a ledger entry (Expenses, Income) — was previously
 *  copy-pasted identically into each page. Shows what's already attached when editing an
 *  existing entry, the not-yet-uploaded file picked for a new one, or a placeholder. */
export function BillPreview({
  editingId,
  groupId,
  billFile,
}: {
  editingId: string | null;
  groupId: string;
  billFile: File | null;
}) {
  if (editingId) {
    return <ImageGallery groupId={groupId} ownerType={LEDGER_ENTRY_OWNER_TYPE} ownerId={editingId} accept={BILL_ACCEPT} />;
  }
  if (billFile) {
    return <FilePreview file={billFile} />;
  }
  return <p className="muted" style={{ fontSize: 12 }}>No bill attached yet.</p>;
}

/** The "attach a bill" file input row — only shown while creating (an existing entry's
 *  bill is managed through the {@link ImageGallery} preview instead, same convention this
 *  codebase already uses for Order Tracker's own image attachments). `capture="environment"`
 *  (ui-13) is just a hint — mobile browsers that support it open straight to the back
 *  camera instead of a file browser; desktop and unsupported browsers ignore it and fall
 *  back to the ordinary file picker, so it's safe to set unconditionally. */
export function BillFileInput({
  id,
  editingId,
  onChange,
}: {
  id: string;
  editingId: string | null;
  onChange: (file: File | null) => void;
}) {
  if (editingId) return null;
  return (
    <div className="form-row">
      <label htmlFor={id}>Attach a bill (optional)</label>
      <input id={id} type="file" accept={BILL_ACCEPT} capture="environment" onChange={(e) => onChange(e.target.files?.[0] ?? null)} />
    </div>
  );
}
