import { createContext, useContext, useEffect, useMemo, useState, ReactNode } from "react";
import { api } from "../api/client";

interface TeamUser {
  id: string;
  name: string;
  email: string;
  role: string;
}

interface Ctx {
  users: TeamUser[];
  nameOf: (id: string | null | undefined) => string;
}

const UsersCtx = createContext<Ctx>({ users: [], nameOf: () => "Unassigned" });

export function UsersProvider({ children }: { children: ReactNode }) {
  const [users, setUsers] = useState<TeamUser[]>([]);

  useEffect(() => {
    api.get<TeamUser[]>("/users").then(setUsers).catch(() => setUsers([]));
  }, []);

  const value = useMemo<Ctx>(() => {
    const byId = new Map(users.map((u) => [u.id, u.name]));
    return {
      users,
      nameOf: (id) => (id && byId.get(id)) || "Unassigned",
    };
  }, [users]);

  return <UsersCtx.Provider value={value}>{children}</UsersCtx.Provider>;
}

export function useUsers(): Ctx {
  return useContext(UsersCtx);
}
