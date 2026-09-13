import { createContext, useCallback, useContext, useEffect, useState, ReactNode } from "react";
import { api } from "../api/client";
import { useGroups } from "./GroupContext";

interface Ctx {
  categories: string[];
  refresh: () => void;
}

const GroupCategoriesCtx = createContext<Ctx>({ categories: [], refresh: () => {} });

/**
 * The current group's own category list — Backlog Tracker's data, not part of the
 * commons GroupView (design: platform integration), so it's fetched separately here and
 * re-fetched whenever the current group changes.
 */
export function GroupCategoriesProvider({ children }: { children: ReactNode }) {
  const { currentGroupId } = useGroups();
  const [categories, setCategories] = useState<string[]>([]);

  const refresh = useCallback(() => {
    if (!currentGroupId) {
      setCategories([]);
      return;
    }
    api
      .get<{ categories: string[] }>(`/groups/${currentGroupId}/categories`)
      .then((r) => setCategories(r.categories))
      .catch(() => setCategories([]));
  }, [currentGroupId]);

  useEffect(refresh, [refresh]);

  return (
    <GroupCategoriesCtx.Provider value={{ categories, refresh }}>{children}</GroupCategoriesCtx.Provider>
  );
}

export function useGroupCategories(): Ctx {
  return useContext(GroupCategoriesCtx);
}
