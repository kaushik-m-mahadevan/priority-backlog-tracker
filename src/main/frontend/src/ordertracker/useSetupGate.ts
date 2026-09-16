import { useCallback, useEffect, useState } from "react";
import { orderTrackerApi } from "./api";
import type { Creator } from "./types";

export type GateStatus = "loading" | "wizard" | "profile-gate" | "ready";

/** Decides what OrderTrackerLayout should show instead of the normal Outlet for the
 *  current business: the first-time setup wizard (design decision: forced setup for
 *  newly-created businesses only), a one-field "set your location & hours" gate (design
 *  decision: a hard gate for anyone — new member or old — who hasn't set one up, since
 *  the assignee picker silently excludes them otherwise), or nothing (business is fully
 *  set up and the caller has a profile). Re-fetches both business-config and the caller's
 *  own creator profile whenever the business changes or `refresh()` is called (the wizard
 *  and the profile gate both call it once they've finished their own step). */
export function useSetupGate(groupId: string | null) {
  const [status, setStatus] = useState<GateStatus>("loading");
  const [profile, setProfile] = useState<Creator | null>(null);

  const refresh = useCallback(async () => {
    if (!groupId) {
      setStatus("ready");
      return;
    }
    setStatus("loading");
    const [config, me] = await Promise.all([
      orderTrackerApi.businessConfig(groupId),
      orderTrackerApi.myCreatorProfile(groupId).catch(() => null),
    ]);
    setProfile(me ?? null);
    if (!config.setupComplete) {
      setStatus("wizard");
    } else if (!me) {
      setStatus("profile-gate");
    } else {
      setStatus("ready");
    }
  }, [groupId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { status, profile, refresh };
}
