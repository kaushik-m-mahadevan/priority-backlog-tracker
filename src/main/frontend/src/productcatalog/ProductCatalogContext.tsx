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

const KEY = "pbt.productcatalog.groupId";
const APPLET_KEY = "productcatalog";

interface Ctx {
  catalogGroups: GroupView[];
  loading: boolean;
  currentCatalogGroup: GroupView | null;
  currentGroupId: string | null;
  setCurrentCatalogGroup: (id: string) => void;
  createCatalogGroup: (name: string) => Promise<GroupView>;
  refresh: () => Promise<void>;
}

const ProductCatalogCtx = createContext<Ctx | undefined>(undefined);

function readStored(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

/** Product Catalog's own group picker — same shape as Material Inventory's
 *  MaterialInventoryContext and Finance Tracker's FinanceGroupContext, deliberately
 *  separate from both, since a group is scoped to one applet (platform integration
 *  decision). A Product Catalog group is its own independent permission scope, connected
 *  to an Order Tracker business only via GroupLink. */
export function ProductCatalogProvider({ children }: { children: ReactNode }) {
  const [catalogGroups, setCatalogGroups] = useState<GroupView[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(readStored());

  const refresh = useCallback(async () => {
    try {
      const list = await api.get<GroupView[]>(`/groups?appletKey=${APPLET_KEY}`);
      setCatalogGroups(list);
      setSelectedId((prev) => {
        if (prev && list.some((g) => g.id === prev)) return prev;
        return list[0]?.id ?? null;
      });
    } catch {
      setCatalogGroups([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const setCurrentCatalogGroup = useCallback((id: string) => {
    setSelectedId(id);
    try {
      localStorage.setItem(KEY, id);
    } catch {
      /* private mode */
    }
  }, []);

  const createCatalogGroup = useCallback(
    async (name: string) => {
      const created = await api.post<GroupView>(`/groups?appletKey=${APPLET_KEY}`, { name });
      await refresh();
      setCurrentCatalogGroup(created.id);
      return created;
    },
    [refresh, setCurrentCatalogGroup]
  );

  const value = useMemo<Ctx>(() => {
    const currentCatalogGroup = catalogGroups.find((g) => g.id === selectedId) ?? null;
    return {
      catalogGroups,
      loading,
      currentCatalogGroup,
      currentGroupId: currentCatalogGroup?.id ?? null,
      setCurrentCatalogGroup,
      createCatalogGroup,
      refresh,
    };
  }, [catalogGroups, loading, selectedId, setCurrentCatalogGroup, createCatalogGroup, refresh]);

  return <ProductCatalogCtx.Provider value={value}>{children}</ProductCatalogCtx.Provider>;
}

export function useProductCatalog(): Ctx {
  const ctx = useContext(ProductCatalogCtx);
  if (!ctx) throw new Error("useProductCatalog must be used within ProductCatalogProvider");
  return ctx;
}
