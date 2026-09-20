import { useMemo, type ReactNode } from "react";
import { createAppletGroupContext } from "../lib/createAppletGroupContext";
import type { GroupView } from "../types";

interface Ctx {
  financeGroups: GroupView[];
  loading: boolean;
  currentFinanceGroup: GroupView | null;
  currentGroupId: string | null;
  setCurrentFinanceGroup: (id: string) => void;
  createFinanceGroup: (name: string) => Promise<GroupView>;
  refresh: () => Promise<void>;
}

const { Provider, useAppletGroup } = createAppletGroupContext("financetracker", "financetracker");

/** Finance Tracker's own group picker — same shape as Order Tracker's BusinessContext,
 *  deliberately separate from it and from Backlog Tracker's GroupContext, since a group
 *  is scoped to one applet (platform integration decision). A Finance Tracker group is
 *  its own independent permission scope, on purpose — see GroupLink for how one gets
 *  connected to an Order Tracker business. Built on the shared createAppletGroupContext
 *  factory (fdup-4); this module just renames the generic shape to Finance Tracker's own
 *  historical field names so every existing consumer keeps working unchanged. */
export function FinanceGroupProvider({ children }: { children: ReactNode }) {
  return <Provider>{children}</Provider>;
}

export function useFinanceGroup(): Ctx {
  const ctx = useAppletGroup();
  return useMemo(
    () => ({
      financeGroups: ctx.groups,
      loading: ctx.loading,
      currentFinanceGroup: ctx.currentGroup,
      currentGroupId: ctx.currentGroupId,
      setCurrentFinanceGroup: ctx.setCurrentGroup,
      createFinanceGroup: ctx.createGroup,
      refresh: ctx.refresh,
    }),
    [ctx]
  );
}
