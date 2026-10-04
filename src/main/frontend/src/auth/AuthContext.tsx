import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, ReactNode } from "react";
import { api, setToken, getToken, setSessionRejectedHandler } from "../api/client";
import { useIdleSession } from "./useIdleSession";
import type { User } from "../types";

interface RegisterInput {
  name: string;
  handle: string;
  email: string;
  password: string;
  confirmPassword: string;
}

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  refresh: () => Promise<void>;
  logout: () => void;
  /** Why the user was signed out without asking to be (timeout / ended elsewhere); the
   *  login page shows it. Cleared on the next successful login. */
  notice: string | null;
}

const AuthCtx = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState<string | null>(null);
  const [timeoutMinutes, setTimeoutMinutes] = useState<number | null>(null);
  // An explicit sign-out (or idle sign-out) is already in flight: a 401 on that very
  // request just means the server got there first, not "your session ended".
  const signingOut = useRef(false);

  const dropSession = useCallback((message: string | null) => {
    setToken(null);
    setUser(null);
    setNotice(message);
  }, []);

  useEffect(() => {
    setSessionRejectedHandler(() => {
      if (!signingOut.current) dropSession("Your session ended. Please sign in again.");
    });
    return () => setSessionRejectedHandler(null);
  }, [dropSession]);

  const userId = user?.id ?? null;
  useEffect(() => {
    if (!userId) {
      setTimeoutMinutes(null);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const policy = await api.get<{ timeoutMinutes: number }>("/auth/session-policy");
        if (!cancelled && typeof policy?.timeoutMinutes === "number") setTimeoutMinutes(policy.timeoutMinutes);
      } catch {
        /* no timer without a known timeout — the server still enforces it */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [userId]);

  useIdleSession({
    enabled: !!userId,
    timeoutMinutes,
    onHeartbeat: () => {
      void Promise.resolve(api.post("/auth/heartbeat")).catch(() => {});
    },
    onIdle: () => {
      signingOut.current = true;
      void Promise.resolve(api.post("/auth/logout?inactive=true")).catch(() => {});
      dropSession(
        `You were signed out after ${timeoutMinutes} ${timeoutMinutes === 1 ? "minute" : "minutes"} of inactivity.`,
      );
    },
  });

  useEffect(() => {
    if (!getToken()) {
      setLoading(false);
      return;
    }
    api
      .get<User>("/auth/me")
      .then(setUser)
      .catch(() => {
        setToken(null);
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      async login(email, password) {
        const res = await api.post<{ token: string; user: User }>("/auth/login", {
          email,
          password,
        });
        signingOut.current = false;
        setNotice(null);
        setToken(res.token);
        setUser(res.user);
      },
      async register(input) {
        const res = await api.post<{ token: string; user: User }>("/auth/register", input);
        signingOut.current = false;
        setNotice(null);
        setToken(res.token);
        setUser(res.user);
      },
      async refresh() {
        try {
          setUser(await api.get<User>("/auth/me"));
        } catch {
          setToken(null);
          setUser(null);
        }
      },
      logout() {
        // The request captures the token synchronously, so it is sent before the token is
        // cleared; the server closes the session and records the sign-out time.
        signingOut.current = true;
        void Promise.resolve(api.post("/auth/logout")).catch(() => {});
        dropSession(null);
      },
      notice,
    }),
    [user, loading, notice, dropSession],
  );

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
