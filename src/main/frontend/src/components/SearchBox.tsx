import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { useDismissableMenu } from "../lib/useDismissableMenu";
import { usePopoverPosition } from "../lib/usePopoverPosition";

interface SearchResult {
  category: string;
  id: string;
  title: string;
  subtitle: string | null;
  path: string;
}

/** ad-5: one header search box scoped to the current group plus whatever it's linked to
 *  (see CrossAppletSearchService on the backend, which resolves that scope generically —
 *  this component just calls one endpoint and doesn't need to know which applets exist).
 *  Debounced; the backend itself requires 2+ characters. */
export default function SearchBox({ groupId }: { groupId: string }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const { open, setOpen, ref } = useDismissableMenu<HTMLDivElement>();
  const triggerRef = useRef<HTMLInputElement>(null);
  const { popoverRef, style: popoverStyle } = usePopoverPosition(triggerRef, open);
  const navigate = useNavigate();

  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 2) {
      setResults([]);
      setOpen(false);
      return;
    }
    const timer = setTimeout(() => {
      setLoading(true);
      api
        .get<SearchResult[]>(`/search?groupId=${encodeURIComponent(groupId)}&q=${encodeURIComponent(trimmed)}`)
        .then((r) => {
          setResults(r);
          setOpen(true);
        })
        .catch(() => setResults([]))
        .finally(() => setLoading(false));
    }, 250);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, groupId]);

  const go = (r: SearchResult) => {
    setOpen(false);
    setQuery("");
    navigate(r.path);
  };

  return (
    <div className="searchbox" ref={ref}>
      <input
        ref={triggerRef}
        type="search"
        aria-label="Search"
        placeholder="Search…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onFocus={() => {
          if (results.length > 0) setOpen(true);
        }}
      />
      {open && (
        <div className="popover searchbox-pop" role="listbox" ref={popoverRef} style={popoverStyle}>
          {loading ? (
            <p className="muted" style={{ margin: 0 }}>
              Searching…
            </p>
          ) : results.length === 0 ? (
            <p className="empty" style={{ margin: 0 }}>
              No matches.
            </p>
          ) : (
            results.map((r) => (
              <button type="button" key={`${r.category}-${r.id}`} className="searchbox-result" onClick={() => go(r)}>
                <span className="searchbox-category">{r.category}</span>
                <span className="searchbox-title">{r.title}</span>
                {r.subtitle && <span className="searchbox-subtitle muted">{r.subtitle}</span>}
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}
