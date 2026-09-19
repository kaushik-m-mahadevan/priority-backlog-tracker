/** Prev/next page controls, shown only when there's more than one page — previously
 *  duplicated verbatim between ItemsPage and ArchivePage. */
export function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="pager">
      <button disabled={page === 0} onClick={() => onChange(page - 1)}>
        ‹ Prev
      </button>
      <span className="muted">
        Page {page + 1} of {totalPages}
      </span>
      <button disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next ›
      </button>
    </div>
  );
}
