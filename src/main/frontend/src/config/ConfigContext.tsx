import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { api } from "../api/client";
import type { AppConfig } from "../types";

const ConfigCtx = createContext<AppConfig | null>(null);

export function ConfigProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<AppConfig | null>(null);

  useEffect(() => {
    api.get<AppConfig>("/config").then(setConfig).catch(() => setConfig(null));
  }, []);

  return <ConfigCtx.Provider value={config}>{children}</ConfigCtx.Provider>;
}

export function useConfig(): AppConfig | null {
  return useContext(ConfigCtx);
}
