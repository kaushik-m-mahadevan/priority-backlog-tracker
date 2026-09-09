import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  ReactNode,
} from "react";
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
  /** stable position of a user in the sorted team list, or -1 — used to give each
      distinct person a distinct avatar without hash collisions on tiny teams */
  indexOf: (id: string | null | undefined) => number;
  /** re-fetch the team list (call after adding a member or changing a role) */
  refresh: () => void;
}

const UsersCtx = createContext<Ctx>({
  users: [],
  nameOf: () => "Unassigned",
  indexOf: () => -1,
  refresh: () => {},
});

export function UsersProvider({ children }: { children: ReactNode }) {
  const [users, setUsers] = useState<TeamUser[]>([]);

  const refresh = useCallback(() => {
    api
      .get<TeamUser[]>("/users")
      .then(setUsers)
      .catch(() => setUsers([]));
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const value = useMemo<Ctx>(() => {
    const byId = new Map(users.map((u) => [u.id, u.name]));
    const idxById = new Map(users.map((u, i) => [u.id, i]));
    return {
      users,
      nameOf: (id) => (id && byId.get(id)) || "Unassigned",
      indexOf: (id) => (id ? idxById.get(id) ?? -1 : -1),
      refresh,
    };
  }, [users, refresh]);

  return <UsersCtx.Provider value={value}>{children}</UsersCtx.Provider>;
}

export function useUsers(): Ctx {
  return useContext(UsersCtx);
}
