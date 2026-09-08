import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import Bell from "./Bell";
import { GearIcon } from "./icons";

export default function Layout() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  return (
    <div className="app">
      <nav className="nav">
        <Link to="/" className="brand">
          ◆ Backlog Tracker
        </Link>
        <NavLink to="/items">Items</NavLink>
        <NavLink to="/archive">Completed</NavLink>
        <span className="spacer" />
        <Bell />
        <button
          className="iconbtn"
          aria-label="Settings"
          title="Settings"
          onClick={() => nav("/settings")}
        >
          <GearIcon />
        </button>
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
