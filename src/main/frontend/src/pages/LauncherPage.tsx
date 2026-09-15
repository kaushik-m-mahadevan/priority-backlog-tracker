import { Link } from "react-router-dom";
import AppHeader from "../components/AppHeader";
import { useAuth } from "../auth/AuthContext";

/**
 * Post-login landing page — pick which applet to open (design: platform integration).
 * No appletName on the header here — there's no "current applet" to name on the home
 * screen itself, just the home button (a no-op here, but consistent everywhere else)
 * plus the always-present bell and account menu.
 */
export default function LauncherPage() {
  const { user } = useAuth();

  return (
    <div className="app">
      <AppHeader />
      <div className="launcher">
        <div className="launcher-head">
          <h1 className="page-title">Hey, {user?.name?.split(" ")[0] ?? "there"}</h1>
          <p className="page-sub">Pick where you want to work.</p>
        </div>

        <div className="applet-grid">
          <Link to="/backlog" className="applet-card">
            <span className="applet-icon" aria-hidden="true">
              ◆
            </span>
            <h2>Priority Backlog Tracker</h2>
            <p>Rank the backlog, track who's on what, and see what's slipping.</p>
          </Link>

          <Link to="/ordertracker" className="applet-card">
            <span className="applet-icon" aria-hidden="true">
              ✂
            </span>
            <h2>Order Tracker</h2>
            <p>Track customers, orders, and payments for a crochet business.</p>
          </Link>

          <Link to="/financetracker" className="applet-card">
            <span className="applet-icon" aria-hidden="true">
              💰
            </span>
            <h2>Finance Tracker</h2>
            <p>Track expenses, income, splits, and who owes what.</p>
          </Link>

          <Link to="/materialinventory" className="applet-card">
            <span className="applet-icon" aria-hidden="true">
              🧶
            </span>
            <h2>Material Inventory</h2>
            <p>Track yarn on hand, and request or transfer it between team members.</p>
          </Link>

          <Link to="/settings" className="applet-card">
            <span className="applet-icon" aria-hidden="true">
              ⚙
            </span>
            <h2>Settings</h2>
            <p>Profile, theme, timezone, and each applet's own configuration.</p>
          </Link>

          {user?.role === "ADMIN" && (
            <Link to="/admin" className="applet-card">
              <span className="applet-icon" aria-hidden="true">
                ◇
              </span>
              <h2>Admin Console</h2>
              <p>Approve accounts, handle password requests, see everyone on the platform.</p>
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
