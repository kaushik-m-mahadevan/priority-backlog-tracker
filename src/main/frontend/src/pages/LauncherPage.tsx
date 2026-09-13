import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

/**
 * Post-login landing page — pick which applet to open (design: platform integration).
 * Order Tracker doesn't exist yet (Phase 6); its box is shown but disabled so the launcher
 * doesn't need reshaping again once it lands, and so the eventual "more applets" story is
 * visible now rather than looking like an afterthought later.
 */
export default function LauncherPage() {
  const { user } = useAuth();

  return (
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

        <div className="applet-card is-disabled" aria-disabled="true">
          <span className="applet-icon" aria-hidden="true">
            ✂
          </span>
          <h2>Order Tracker</h2>
          <p>Coming soon.</p>
        </div>

        <Link to="/settings" className="applet-card">
          <span className="applet-icon" aria-hidden="true">
            ⚙
          </span>
          <h2>Settings</h2>
          <p>Profile, theme, timezone, and each applet's own configuration.</p>
        </Link>
      </div>
    </div>
  );
}
