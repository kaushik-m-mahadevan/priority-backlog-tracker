import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { api, ApiError } from "../api/client";
import { runCelebration, collapseRow } from "../lib/celebrate";
import ItemFormModal from "../components/ItemFormModal";
import LeftDock from "../components/LeftDock";
import Grove from "../components/Grove";
import { Creature } from "../components/Creature";
import { useUsers } from "../users/UsersContext";
import { PriorityMark } from "../components/PriorityMark";
import { EffortIcon } from "../components/EffortIcon";
import { DueMark } from "../components/DueMark";
import { useDock } from "../dock/DockContext";
import type { Item, NeedsAttention, RankedItem, WorkloadOverview } from "../types";

export default function DashboardPage() {
  const [top, setTop] = useState<RankedItem[] | null>(null);
  const [quick, setQuick] = useState<RankedItem[] | null>(null);
  const [attention, setAttention] = useState<NeedsAttention | null>(null);
  const [team, setTeam] = useState<WorkloadOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<Item | null>(null);
  const [groveKey, setGroveKey] = useState(0);
  const { nameOf } = useUsers();
  const { shown } = useDock();

  const listRef = useRef<HTMLDivElement>(null);
  const [railMax, setRailMax] = useState<number | undefined>();

  function load() {
    Promise.all([
      api.get<RankedItem[]>("/items/top?limit=10"),
      api.get<RankedItem[]>("/items/quick-wins?limit=8"),
      api.get<NeedsAttention>("/insights/needs-attention"),
      api.get<WorkloadOverview>("/insights/workload"),
    ])
      .then(([t, q, a, w]) => {
        setTop(t);
        setQuick(q);
        setAttention(a);
        setTeam(w);
      })
      .catch((e) => setError(e.message));
  }
  useEffect(load, []);

  async function handleComplete(item: Item, terminal: string = "RESOLVED") {
    setError(null);
    const rowEl = listRef.current?.querySelector<HTMLElement>(`[data-id="${item.id}"]`);
    // let the request run under the ceremony so the longer animation never feels like lag
    const apiCall = api.post(`/items/${item.id}/complete`, { terminalStatus: terminal });
    try {
      if (rowEl) await runCelebration(rowEl);
      await apiCall;
      if (rowEl) await collapseRow(rowEl);
    } catch (e) {
      rowEl?.classList.remove("completing");
      setError(e instanceof ApiError ? e.message : "Could not complete the item");
      return;
    }
    setGroveKey((k) => k + 1);
    load();
  }

  useLayoutEffect(() => {
    function measure() {
      if (listRef.current) setRailMax(listRef.current.offsetHeight);
    }
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, [top]);

  return (
    <div data-dock={shown ? "on" : "off"}>
      {error && <div className="error">{error}</div>}

      <div className={`dash${shown ? " with-dock" : ""}`}>
        {shown ? (
          <LeftDock
            maxHeight={railMax}
            quick={quick}
            attention={attention}
            team={team}
            onOpen={setEditing}
          />
        ) : (
          <div className="dash-gutter" aria-hidden="true" />
        )}

        <div className="dash-center">
          <div className="dash-head">
            <h1 className="page-title" style={{ margin: 0 }}>
              The Pecking Order
            </h1>
            <div className="head-grove">
              <Grove compact refreshKey={groveKey} />
            </div>
          </div>

          <div className="plist" ref={listRef}>
            {top && top.length === 0 && (
              <div className="empty" style={{ padding: 18 }}>Nothing in the backlog yet.</div>
            )}
            {top?.map((r) => (
              <div
                key={r.item.id}
                data-id={r.item.id}
                className="prow"
                role="button"
                onClick={() => setEditing(r.item)}
              >
                <Creature
                  seed={r.item.ownerId}
                  label={nameOf(r.item.ownerId)}
                  inProgress={r.item.status === "IN_PROGRESS"}
                />
                <PriorityMark priority={r.item.priority} />
                <span className="title">{r.item.title}</span>
                <span className="meta">
                  <EffortIcon effort={r.item.effort} />
                  <DueMark iso={r.item.dueDate} />
                  <button
                    className="prow-check"
                    title="Mark done"
                    aria-label={`Mark "${r.item.title}" done`}
                    onClick={(e) => {
                      e.stopPropagation();
                      handleComplete(r.item);
                    }}
                  >
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path
                        d="M5 12.5l4.5 4.5L19 7"
                        stroke="currentColor"
                        strokeWidth="2.4"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </button>
                </span>
              </div>
            ))}
          </div>
        </div>

        {!shown && (
          <aside className="dash-aside">
            <Grove solo refreshKey={groveKey} />
          </aside>
        )}
      </div>

      {editing && (
        <ItemFormModal
          existing={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            load();
          }}
          onComplete={(terminal) => {
            const item = editing;
            setEditing(null);
            // let the modal-close render flush, then animate the row
            if (item) setTimeout(() => handleComplete(item, terminal), 0);
          }}
        />
      )}
    </div>
  );
}
