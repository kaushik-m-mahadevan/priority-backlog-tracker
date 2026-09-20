import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api } from "../api/client";
import type { GroupView } from "../types";

export interface AppletGroupCtx {
  groups: GroupView[];
  loading: boolean;
  currentGroup: GroupView | null;
  currentGroupId: string | null;
  setCurrentGroup: (id: string) => void;
  createGroup: (name: string) => Promise<GroupView>;
  refresh: () => Promise<void>;
}

/** Factory (fdup-4) for the "own group picker" pattern that was hand-copied identically
 *  into Finance Tracker, Material Inventory, and Product Catalog: load this applet's
 *  groups, remember the selected one in localStorage, create a new one and switch to it.
 *  Each applet's group is its own independent permission scope, connected to an Order
 *  Tracker business only via GroupLink (platform integration decision: a group is scoped
 *  to one applet, switchers must never see each other's groups) — so this stays a factory
 *  producing a separate Provider/hook pair per applet, never one shared context instance.
 *  Each applet's own Context module wraps the returned pair and re-exposes its historical
 *  field names (e.g. `financeGroups`, `currentFinanceGroup`) so every existing consumer
 *  across the app keeps working unchanged. */
export function createAppletGroupContext(appletKey: string, storageNamespace: string) {
  const storageKey = `pbt.${storageNamespace}.groupId`;
  const Ctx = createContext<AppletGroupCtx | undefined>(undefined);

  function readStored(): string | null {
    try {
      return localStorage.getItem(storageKey);
    } catch {
      return null;
    }
  }

  function Provider({ children }: { children: ReactNode }) {
    const [groups, setGroups] = useState<GroupView[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedId, setSelectedId] = useState<string | null>(readStored());

    const refresh = useCallback(async () => {
      try {
        const list = await api.get<GroupView[]>(`/groups?appletKey=${appletKey}`);
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
        localStorage.setItem(storageKey, id);
      } catch {
        /* private mode */
      }
    }, []);

    const createGroup = useCallback(
      async (name: string) => {
        const created = await api.post<GroupView>(`/groups?appletKey=${appletKey}`, { name });
        await refresh();
        setCurrentGroup(created.id);
        return created;
      },
      [refresh, setCurrentGroup]
    );

    const value = useMemo<AppletGroupCtx>(() => {
      const currentGroup = groups.find((g) => g.id === selectedId) ?? null;
      return {
        groups,
        loading,
        currentGroup,
        currentGroupId: currentGroup?.id ?? null,
        setCurrentGroup,
        createGroup,
        refresh,
      };
    }, [groups, loading, selectedId, setCurrentGroup, createGroup, refresh]);

    return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
  }

  function useAppletGroup(): AppletGroupCtx {
    const ctx = useContext(Ctx);
    if (!ctx) throw new Error(`useAppletGroup("${appletKey}") must be used within its Provider`);
    return ctx;
  }

  return { Provider, useAppletGroup };
}
