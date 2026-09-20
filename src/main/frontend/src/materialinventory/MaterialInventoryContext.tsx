import { useMemo, type ReactNode } from "react";
import { createAppletGroupContext } from "../lib/createAppletGroupContext";
import type { GroupView } from "../types";

interface Ctx {
  inventoryGroups: GroupView[];
  loading: boolean;
  currentInventoryGroup: GroupView | null;
  currentGroupId: string | null;
  setCurrentInventoryGroup: (id: string) => void;
  createInventoryGroup: (name: string) => Promise<GroupView>;
  refresh: () => Promise<void>;
}

const { Provider, useAppletGroup } = createAppletGroupContext("materialinventory", "materialinventory");

/** Material Inventory's own group picker — same shape as Finance Tracker's
 *  FinanceGroupContext and Order Tracker's BusinessContext, deliberately separate from
 *  both, since a group is scoped to one applet (platform integration decision). A
 *  Material Inventory group is its own independent permission scope, connected to an
 *  Order Tracker business only via GroupLink. Built on the shared createAppletGroupContext
 *  factory (fdup-4); this module just renames the generic shape to Material Inventory's
 *  own historical field names so every existing consumer keeps working unchanged. */
export function MaterialInventoryProvider({ children }: { children: ReactNode }) {
  return <Provider>{children}</Provider>;
}

export function useMaterialInventory(): Ctx {
  const ctx = useAppletGroup();
  return useMemo(
    () => ({
      inventoryGroups: ctx.groups,
      loading: ctx.loading,
      currentInventoryGroup: ctx.currentGroup,
      currentGroupId: ctx.currentGroupId,
      setCurrentInventoryGroup: ctx.setCurrentGroup,
      createInventoryGroup: ctx.createGroup,
      refresh: ctx.refresh,
    }),
    [ctx]
  );
}
