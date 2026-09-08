import { createContext, useContext, useMemo, useState, ReactNode } from "react";

export type DockSection = "quick" | "attention" | "team";

interface Ctx {
  /** whole dock visible at all (default false) */
  shown: boolean;
  setShown: (v: boolean) => void;
  /** which panel is expanded next to the strip; null = strip only */
  active: DockSection | null;
  setActive: (s: DockSection | null) => void;
}

const DockCtx = createContext<Ctx | undefined>(undefined);

function s(key: string, fallback: string) {
  try {
    return sessionStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}
function w(key: string, val: string) {
  try {
    sessionStorage.setItem(key, val);
  } catch {
    /* ignore */
  }
}

export function DockProvider({ children }: { children: ReactNode }) {
  const [shown, setShownState] = useState(() => s("pbt.dock.shown", "0") === "1");
  const [active, setActiveState] = useState<DockSection | null>(() => {
    const v = s("pbt.dock.active", "quick");
    return v === "attention" || v === "team" || v === "quick" ? (v as DockSection) : "quick";
  });

  const value = useMemo<Ctx>(
    () => ({
      shown,
      setShown: (v) => {
        setShownState(v);
        w("pbt.dock.shown", v ? "1" : "0");
      },
      active,
      setActive: (sec) => {
        setActiveState(sec);
        if (sec) w("pbt.dock.active", sec);
      },
    }),
    [shown, active],
  );

  return <DockCtx.Provider value={value}>{children}</DockCtx.Provider>;
}

export function useDock(): Ctx {
  const c = useContext(DockCtx);
  if (!c) throw new Error("useDock outside provider");
  return c;
}
