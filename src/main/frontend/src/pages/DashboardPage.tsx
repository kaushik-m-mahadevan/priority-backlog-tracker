import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "../api/client";
import { runCelebration, collapseRow } from "../lib/celebrate";
import { useItemsChanged, notifyItemsChanged } from "../lib/events";
import ItemFormModal from "../components/ItemFormModal";
import LeftDock from "../components/LeftDock";
import Grove from "../components/Grove";
import EmptyLeaf from "../components/EmptyLeaf";
import NoGroupNotice from "../components/NoGroupNotice";
import { Creature } from "../components/Creature";
import { useGroups } from "../groups/GroupContext";
import { useUsers } from "../users/UsersContext";
import { PriorityMark } from "../components/PriorityMark";
import { EffortIcon } from "../components/EffortIcon";
import { DueMark } from "../components/DueMark";
import { useDock } from "../dock/DockContext";
import type { Item, NeedsAttention, RankedItem, TopList, WorkloadOverview } from "../types";

export default function DashboardPage() {
  const [top, setTop] = useState<RankedItem[] | null>(null);
  const [overPinned, setOverPinned] = useState(false);
  const [archivePending, setArchivePending] = useState<Set<string>>(new Set());
  const [quick, setQuick] = useState<RankedItem[] | null>(null);
  const [attention, setAttention] = useState<NeedsAttention | null>(null);
  const [team, setTeam] = useState<WorkloadOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<Item | null>(null);
  const [adding, setAdding] = useState(false);
  const [groveKey, setGroveKey] = useState(0);
  const { nameOf } = useUsers();
  const { shown } = useDock();
  const { currentGroupId } = useGroups();

  const listRef = useRef<HTMLDivElement>(null);

  const load = useCallback(() => {
    if (!currentGroupId) {
      setTop([]);
      setQuick([]);
      setAttention(null);
      setTeam(null);
      return;
    }
    const g = `groupId=${currentGroupId}`;
    Promise.all([
      api.get<TopList>(`/items/top?limit=10&${g}`),
      api.get<RankedItem[]>(`/items/quick-wins?limit=8&${g}`),
      api.get<NeedsAttention>(`/insights/needs-attention?${g}`),
      api.get<WorkloadOverview>(`/insights/workload?${g}`),
      api.get<{ itemId: string }[]>(`/archive-requests?${g}`),
    ])
      .then(([t, q, a, w, ar]) => {
        setTop(t.items);
        setOverPinned(t.overPinned);
        setQuick(q);
        setAttention(a);
        setTeam(w);
        setArchivePending(new Set(ar.map((r) => r.itemId)));
      })
      .catch((e) => setError(e.message));
  }, [currentGroupId]);
  useEffect(load, [load]);
  useItemsChanged(load);

  async function handleComplete(item: Item, terminal: string = "RESOLVED") {
    setError(null);

    if (terminal === "ARCHIVED") {
      const note = window.prompt(
        `Request to archive "${item.title}"?\nEvery group member has to approve before it's archived.\n\nOptional note:`,
        "",
      );
      if (note === null) return;
      try {
        await api.post(`/items/${item.id}/archive-requests`, { note });
        notifyItemsChanged();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not raise the archive request");
      }
      return;
    }

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
    notifyItemsChanged(); // refreshes this list + the bell + the panels
  }

  async function togglePin(item: Item) {
    try {
      if (item.pinned) await api.delete(`/items/${item.id}/pin`);
      else await api.post(`/items/${item.id}/pin`, {});
      notifyItemsChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not update the pin");
    }
  }


  if (!currentGroupId) return <NoGroupNotice />;

  return (
    <div data-dock={shown ? "on" : "off"}>
      {error && <div className="error">{error}</div>}

      <div className={`dash${shown ? " with-dock" : ""}`}>
        {shown ? (
          <LeftDock
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
            <button className="primary add-peck" onClick={() => setAdding(true)}>
              + New
            </button>
            <div className="head-grove">
              <Grove compact refreshKey={groveKey} />
            </div>
          </div>

          {overPinned && (
            <div className="hint pin-warn">
              You're pinning a lot — only the top 10 pinned items show here. Unpin some to
              see the rest of the backlog.
            </div>
          )}

          <div className="plist" ref={listRef}>
            {top && top.length === 0 && (
              <EmptyLeaf message="Nothing in the backlog yet — add the first item." />
            )}
            {top?.map((r, idx) => {
              const showDivider =
                r.item.pinned === false && idx > 0 && top[idx - 1].item.pinned === true;
              return (
                <Fragment key={r.item.id}>
                  {showDivider && <div className="pin-divider" aria-hidden="true" />}
                  <div
                    data-id={r.item.id}
                    className={`prow${r.item.pinned ? " pinned" : ""}`}
                    role="button"
                    tabIndex={0}
                    onClick={() => setEditing(r.item)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        setEditing(r.item);
                      }
                    }}
                  >
                    <Creature
                      seed={r.item.ownerId}
                      label={nameOf(r.item.ownerId)}
                      inProgress={r.item.status === "IN_PROGRESS"}
                    />
                    <PriorityMark priority={r.item.priority} />
                    <span className="title">
                      {r.item.title}
                      {archivePending.has(r.item.id) && (
                        <span className="archive-tag" title="Archive requested — awaiting approvals">
                          archive?
                        </span>
                      )}
                    </span>
                    <span className="meta">
                      <button
                        className={`prow-pin${r.item.pinned ? " on" : ""}`}
                        title={r.item.pinned ? "Unpin" : "Pin to the top"}
                        aria-label={r.item.pinned ? `Unpin "${r.item.title}"` : `Pin "${r.item.title}"`}
                        aria-pressed={r.item.pinned}
                        onClick={(e) => {
                          e.stopPropagation();
                          togglePin(r.item);
                        }}
                      >
                        <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true">
                          <path
                            d="M9 3h6l-1 6 3 3v2h-4v5l-1 2-1-2v-5H6v-2l3-3z"
                            fill={r.item.pinned ? "currentColor" : "none"}
                            stroke="currentColor"
                            strokeWidth="1.8"
                            strokeLinejoin="round"
                          />
                        </svg>
                      </button>
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
                </Fragment>
              );
            })}
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

      {adding && (
        <ItemFormModal
          existing={null}
          onClose={() => setAdding(false)}
          onSaved={() => {
            setAdding(false);
            load();
          }}
        />
      )}
    </div>
  );
}
