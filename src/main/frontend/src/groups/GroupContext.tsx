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
import { notifyItemsChanged } from "../lib/events";
import type { GroupView } from "../types";

const KEY = "pbt.groupId";

interface Ctx {
  groups: GroupView[];
  loading: boolean;
  currentGroup: GroupView | null;
  currentGroupId: string | null;
  setCurrentGroup: (id: string) => void;
  refresh: () => Promise<void>;
}

const GroupCtx = createContext<Ctx | undefined>(undefined);

function readStored(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function GroupProvider({ children }: { children: ReactNode }) {
  const [groups, setGroups] = useState<GroupView[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(readStored());

  const refresh = useCallback(async () => {
    try {
      const list = await api.get<GroupView[]>("/groups");
      setGroups(list);
      setSelectedId((prev) => {
        if (prev && list.some((g) => g.id === prev)) return prev;
        return list[0]?.id ?? null;
      });
    } catch {
      setGroups([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const setCurrentGroup = useCallback((id: string) => {
    setSelectedId(id);
    try {
      localStorage.setItem(KEY, id);
    } catch {
      /* private mode */
    }
    notifyItemsChanged();
  }, []);

  const value = useMemo<Ctx>(() => {
    const currentGroup = groups.find((g) => g.id === selectedId) ?? null;
    return {
      groups,
      loading,
      currentGroup,
      currentGroupId: currentGroup?.id ?? null,
      setCurrentGroup,
      refresh,
    };
  }, [groups, loading, selectedId, setCurrentGroup, refresh]);

  return <GroupCtx.Provider value={value}>{children}</GroupCtx.Provider>;
}

export function useGroups(): Ctx {
  const ctx = useContext(GroupCtx);
  if (!ctx) throw new Error("useGroups must be used within GroupProvider");
  return ctx;
}
