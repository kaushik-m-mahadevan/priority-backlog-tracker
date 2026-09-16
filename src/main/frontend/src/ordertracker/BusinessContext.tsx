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

const KEY = "pbt.ordertracker.groupId";
const APPLET_KEY = "ordertracker";

interface Ctx {
  businesses: GroupView[];
  loading: boolean;
  currentBusiness: GroupView | null;
  currentGroupId: string | null;
  setCurrentBusiness: (id: string) => void;
  createBusiness: (name: string) => Promise<GroupView>;
  refresh: () => Promise<void>;
}

const BusinessCtx = createContext<Ctx | undefined>(undefined);

function readStored(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

/** Order Tracker's own group ("business") picker — deliberately separate from Backlog
 *  Tracker's GroupContext, since a group is scoped to one applet (platform integration
 *  decision) and the two must never leak into each other's switcher. */
export function BusinessProvider({ children }: { children: ReactNode }) {
  const [businesses, setBusinesses] = useState<GroupView[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(readStored());

  const refresh = useCallback(async () => {
    try {
      const list = await api.get<GroupView[]>(`/groups?appletKey=${APPLET_KEY}`);
      setBusinesses(list);
      setSelectedId((prev) => {
        if (prev && list.some((g) => g.id === prev)) return prev;
        return list[0]?.id ?? null;
      });
    } catch {
      setBusinesses([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const setCurrentBusiness = useCallback((id: string) => {
    setSelectedId(id);
    try {
      localStorage.setItem(KEY, id);
    } catch {
      /* private mode */
    }
  }, []);

  const createBusiness = useCallback(
    async (name: string) => {
      const created = await api.post<GroupView>(`/groups?appletKey=${APPLET_KEY}`, { name });
      // Flips this specific business into "setup pending" (design decision: forced setup
      // only for newly-created businesses, never retroactively) — OrderTrackerLayout's own
      // gate picks this up and routes into the wizard instead of the normal Outlet.
      await api.post(`/ordertracker/groups/${created.id}/business-config/setup/start`);
      await refresh();
      setCurrentBusiness(created.id);
      return created;
    },
    [refresh, setCurrentBusiness]
  );

  const value = useMemo<Ctx>(() => {
    const currentBusiness = businesses.find((g) => g.id === selectedId) ?? null;
    return {
      businesses,
      loading,
      currentBusiness,
      currentGroupId: currentBusiness?.id ?? null,
      setCurrentBusiness,
      createBusiness,
      refresh,
    };
  }, [businesses, loading, selectedId, setCurrentBusiness, createBusiness, refresh]);

  return <BusinessCtx.Provider value={value}>{children}</BusinessCtx.Provider>;
}

export function useBusiness(): Ctx {
  const ctx = useContext(BusinessCtx);
  if (!ctx) throw new Error("useBusiness must be used within BusinessProvider");
  return ctx;
}
