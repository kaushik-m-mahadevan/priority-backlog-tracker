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

const KEY = "pbt.materialinventory.groupId";
const APPLET_KEY = "materialinventory";

interface Ctx {
  inventoryGroups: GroupView[];
  loading: boolean;
  currentInventoryGroup: GroupView | null;
  currentGroupId: string | null;
  setCurrentInventoryGroup: (id: string) => void;
  createInventoryGroup: (name: string) => Promise<GroupView>;
  refresh: () => Promise<void>;
}

const MaterialInventoryCtx = createContext<Ctx | undefined>(undefined);

function readStored(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

/** Material Inventory's own group picker — same shape as Finance Tracker's
 *  FinanceGroupContext and Order Tracker's BusinessContext, deliberately separate from
 *  both, since a group is scoped to one applet (platform integration decision). A
 *  Material Inventory group is its own independent permission scope, connected to an
 *  Order Tracker business only via GroupLink. */
export function MaterialInventoryProvider({ children }: { children: ReactNode }) {
  const [inventoryGroups, setInventoryGroups] = useState<GroupView[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(readStored());

  const refresh = useCallback(async () => {
    try {
      const list = await api.get<GroupView[]>(`/groups?appletKey=${APPLET_KEY}`);
      setInventoryGroups(list);
      setSelectedId((prev) => {
        if (prev && list.some((g) => g.id === prev)) return prev;
        return list[0]?.id ?? null;
      });
    } catch {
      setInventoryGroups([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const setCurrentInventoryGroup = useCallback((id: string) => {
    setSelectedId(id);
    try {
      localStorage.setItem(KEY, id);
    } catch {
      /* private mode */
    }
  }, []);

  const createInventoryGroup = useCallback(
    async (name: string) => {
      const created = await api.post<GroupView>(`/groups?appletKey=${APPLET_KEY}`, { name });
      await refresh();
      setCurrentInventoryGroup(created.id);
      return created;
    },
    [refresh, setCurrentInventoryGroup]
  );

  const value = useMemo<Ctx>(() => {
    const currentInventoryGroup = inventoryGroups.find((g) => g.id === selectedId) ?? null;
    return {
      inventoryGroups,
      loading,
      currentInventoryGroup,
      currentGroupId: currentInventoryGroup?.id ?? null,
      setCurrentInventoryGroup,
      createInventoryGroup,
      refresh,
    };
  }, [inventoryGroups, loading, selectedId, setCurrentInventoryGroup, createInventoryGroup, refresh]);

  return <MaterialInventoryCtx.Provider value={value}>{children}</MaterialInventoryCtx.Provider>;
}

export function useMaterialInventory(): Ctx {
  const ctx = useContext(MaterialInventoryCtx);
  if (!ctx) throw new Error("useMaterialInventory must be used within MaterialInventoryProvider");
  return ctx;
}
