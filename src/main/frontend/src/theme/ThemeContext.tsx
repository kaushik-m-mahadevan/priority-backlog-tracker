import { createContext, useContext, useEffect, useState, ReactNode } from "react";

export type Theme = "dusk" | "tide";
const KEY = "pbt.theme";

function read(): Theme {
  try {
    return localStorage.getItem(KEY) === "tide" ? "tide" : "dusk";
  } catch {
    return "dusk";
  }
}

function apply(t: Theme) {
  document.documentElement.setAttribute("data-theme", t);
}

interface Ctx {
  theme: Theme;
  setTheme: (t: Theme) => void;
}
const ThemeCtx = createContext<Ctx | undefined>(undefined);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(read);

  useEffect(() => {
    apply(theme);
  }, [theme]);

  function setTheme(t: Theme) {
    setThemeState(t);
    try {
      localStorage.setItem(KEY, t);
    } catch {
      /* ignore */
    }
  }

  return <ThemeCtx.Provider value={{ theme, setTheme }}>{children}</ThemeCtx.Provider>;
}

export function useTheme(): Ctx {
  const c = useContext(ThemeCtx);
  if (!c) throw new Error("useTheme outside provider");
  return c;
}
