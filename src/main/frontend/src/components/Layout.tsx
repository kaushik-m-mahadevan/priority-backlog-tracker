import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function Layout() {
  const { user, logout } = useAuth();
  return (
    <div className="app">
      <nav className="nav">
        <span className="brand">◆ Backlog Tracker</span>
        <NavLink to="/" end>
          Dashboard
        </NavLink>
        <NavLink to="/items">Items</NavLink>
        <NavLink to="/archive">Completed</NavLink>
        <NavLink to="/settings">Settings</NavLink>
        <span className="spacer" />
        {user && <span className="who">{user.name}</span>}
        <button className="ghost" onClick={logout}>
          Sign out
        </button>
      </nav>
      <div className="container">
        <Outlet />
      </div>
    </div>
  );
}
