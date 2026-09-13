import { createContext, useCallback, useContext, useEffect, useState, ReactNode } from "react";
import { api } from "../api/client";

interface Ctx {
  enabled: boolean;
  setEnabled: (next: boolean) => Promise<void>;
}

const GroveSettingsCtx = createContext<Ctx>({ enabled: false, setEnabled: async () => {} });

/**
 * The grove-animation opt-in — deliberately not part of the shared User/auth model (design:
 * platform integration). One fetch here, shared by every `<Grove>` instance and the
 * Settings toggle, instead of each Grove re-fetching it independently.
 */
export function GroveSettingsProvider({ children }: { children: ReactNode }) {
  const [enabled, setEnabledState] = useState(false);

  useEffect(() => {
    api
      .get<{ animationsEnabled: boolean }>("/insights/grove-settings")
      .then((r) => setEnabledState(r.animationsEnabled))
      .catch(() => setEnabledState(false));
  }, []);

  const setEnabled = useCallback(async (next: boolean) => {
    const r = await api.patch<{ animationsEnabled: boolean }>("/insights/grove-settings", {
      animationsEnabled: next,
    });
    setEnabledState(r.animationsEnabled);
  }, []);

  return (
    <GroveSettingsCtx.Provider value={{ enabled, setEnabled }}>{children}</GroveSettingsCtx.Provider>
  );
}

export function useGroveSettings(): Ctx {
  return useContext(GroveSettingsCtx);
}
