import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { api } from "../api/client";
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
      <div className="dash-head">
        <div>
          <h1 className="page-title">Priority</h1>
          <p className="page-sub" style={{ margin: 0 }}>
            The ten things that matter most right now.
          </p>
        </div>
        <div className="head-grove">
          <Grove compact />
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      <div className={`dash${shown ? " with-dock" : ""}`}>
        {shown ? (
          <LeftDock maxHeight={railMax} quick={quick} attention={attention} team={team} />
        ) : (
          <aside className="dash-aside">
            <Grove solo />
          </aside>
        )}

        <div className="plist" ref={listRef}>
          {top && top.length === 0 && (
            <div className="empty" style={{ padding: 18 }}>Nothing in the backlog yet.</div>
          )}
          {top?.map((r) => (
            <div key={r.item.id} className="prow" role="button" onClick={() => setEditing(r.item)}>
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
              </span>
            </div>
          ))}
        </div>
      </div>

      {editing && (
        <ItemFormModal
          existing={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            load();
          }}
        />
      )}
    </div>
  );
}
