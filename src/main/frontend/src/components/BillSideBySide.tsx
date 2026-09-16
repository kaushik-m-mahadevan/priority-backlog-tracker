/** Pure layout: a bill/invoice preview sticky beside a form, so you can fill in the
 *  amount while looking at the actual document instead of reconstructing it from memory.
 *  `preview` is whatever's already rendered (a local FilePreview before save, or an
 *  ImageGallery of what's already attached when editing) — this component only lays the
 *  two out side by side and wraps on narrow screens. */
export default function BillSideBySide({ preview, children }: { preview: React.ReactNode; children: React.ReactNode }) {
  return (
    <div style={{ display: "flex", gap: 16, alignItems: "flex-start", flexWrap: "wrap-reverse" }}>
      <div style={{ flex: 2, minWidth: 280 }}>{children}</div>
      <div style={{ flex: 1, minWidth: 220, position: "sticky", top: 16 }}>
        <div className="muted" style={{ fontSize: 12, marginBottom: 6 }}>
          Bill / invoice
        </div>
        {preview}
      </div>
    </div>
  );
}
