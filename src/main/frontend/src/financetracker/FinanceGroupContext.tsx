import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  ReactNode,
} from "react";
import { api } from "../api/client";
import type { GroupView } from "../types";

const KEY = "pbt.financetracker.groupId";
const APPLET_KEY = "financetracker";

interface Ctx {
  financeGroups: GroupView[];
  loading: boolean;
  currentFinanceGroup: GroupView | null;
  currentGroupId: string | null;
  setCurrentFinanceGroup: (id: string) => void;
  createFinanceGroup: (name: string) => Promise<GroupView>;
  refresh: () => Promise<void>;
}

const FinanceGroupCtx = createContext<Ctx | undefined>(undefined);

function readStored(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

/** Finance Tracker's own group picker — same shape as Order Tracker's BusinessContext,
 *  deliberately separate from it and from Backlog Tracker's GroupContext, since a group
 *  is scoped to one applet (platform integration decision). A Finance Tracker group is
 *  its own independent permission scope, on purpose — see GroupLink for how one gets
 *  connected to an Order Tracker business. */
export function FinanceGroupProvider({ children }: { children: ReactNode }) {
  const [financeGroups, setFinanceGroups] = useState<GroupView[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(readStored());

  const refresh = useCallback(async () => {
    try {
      const list = await api.get<GroupView[]>(`/groups?appletKey=${APPLET_KEY}`);
      setFinanceGroups(list);
      setSelectedId((prev) => {
        if (prev && list.some((g) => g.id === prev)) return prev;
        return list[0]?.id ?? null;
      });
    } catch {
      setFinanceGroups([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const setCurrentFinanceGroup = useCallback((id: string) => {
    setSelectedId(id);
    try {
      localStorage.setItem(KEY, id);
    } catch {
      /* private mode */
    }
  }, []);

  const createFinanceGroup = useCallback(
    async (name: string) => {
      const created = await api.post<GroupView>(`/groups?appletKey=${APPLET_KEY}`, { name });
      await refresh();
      setCurrentFinanceGroup(created.id);
      return created;
    },
    [refresh, setCurrentFinanceGroup]
  );

  const value = useMemo<Ctx>(() => {
    const currentFinanceGroup = financeGroups.find((g) => g.id === selectedId) ?? null;
    return {
      financeGroups,
      loading,
      currentFinanceGroup,
      currentGroupId: currentFinanceGroup?.id ?? null,
      setCurrentFinanceGroup,
      createFinanceGroup,
      refresh,
    };
  }, [financeGroups, loading, selectedId, setCurrentFinanceGroup, createFinanceGroup, refresh]);

  return <FinanceGroupCtx.Provider value={value}>{children}</FinanceGroupCtx.Provider>;
}

export function useFinanceGroup(): Ctx {
  const ctx = useContext(FinanceGroupCtx);
  if (!ctx) throw new Error("useFinanceGroup must be used within FinanceGroupProvider");
  return ctx;
}
