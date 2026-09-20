import { useMemo, type ReactNode } from "react";
import { createAppletGroupContext } from "../lib/createAppletGroupContext";
import type { GroupView } from "../types";

interface Ctx {
  catalogGroups: GroupView[];
  loading: boolean;
  currentCatalogGroup: GroupView | null;
  currentGroupId: string | null;
  setCurrentCatalogGroup: (id: string) => void;
  createCatalogGroup: (name: string) => Promise<GroupView>;
  refresh: () => Promise<void>;
}

const { Provider, useAppletGroup } = createAppletGroupContext("productcatalog", "productcatalog");

/** Product Catalog's own group picker — same shape as Material Inventory's
 *  MaterialInventoryContext and Finance Tracker's FinanceGroupContext, deliberately
 *  separate from both, since a group is scoped to one applet (platform integration
 *  decision). A Product Catalog group is its own independent permission scope, connected
 *  to an Order Tracker business only via GroupLink. Built on the shared
 *  createAppletGroupContext factory (fdup-4); this module just renames the generic shape
 *  to Product Catalog's own historical field names so every existing consumer keeps
 *  working unchanged. */
export function ProductCatalogProvider({ children }: { children: ReactNode }) {
  return <Provider>{children}</Provider>;
}

export function useProductCatalog(): Ctx {
  const ctx = useAppletGroup();
  return useMemo(
    () => ({
      catalogGroups: ctx.groups,
      loading: ctx.loading,
      currentCatalogGroup: ctx.currentGroup,
      currentGroupId: ctx.currentGroupId,
      setCurrentCatalogGroup: ctx.setCurrentGroup,
      createCatalogGroup: ctx.createGroup,
      refresh: ctx.refresh,
    }),
    [ctx]
  );
}
