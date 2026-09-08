import { createContext, useCallback, useContext, useEffect, useState, ReactNode } from "react";
import { api } from "../api/client";
import type { AppConfig } from "../types";

interface Ctx {
  config: AppConfig | null;
  refresh: () => void;
}

const ConfigCtx = createContext<Ctx>({ config: null, refresh: () => {} });

export function ConfigProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<AppConfig | null>(null);

  const refresh = useCallback(() => {
    api.get<AppConfig>("/config").then(setConfig).catch(() => setConfig(null));
  }, []);

  useEffect(refresh, [refresh]);

  return <ConfigCtx.Provider value={{ config, refresh }}>{children}</ConfigCtx.Provider>;
}

/** Back-compat: most callers just want the config object. */
export function useConfig(): AppConfig | null {
  return useContext(ConfigCtx).config;
}

export function useConfigCtx(): Ctx {
  return useContext(ConfigCtx);
}
