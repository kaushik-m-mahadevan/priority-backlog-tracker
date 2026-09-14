import { Link } from "react-router-dom";
import { HomeIcon } from "../components/icons";
import SettingsPage from "./SettingsPage";

/**
 * Settings as its own top-level shell (design: platform integration follow-up) — it's a
 * shared hub (account settings plus each applet's own section), not Backlog-Tracker
 * chrome, so it shouldn't render inside Backlog Tracker's own Layout/nav/tabbar.
 */
export default function SettingsShell() {
  return (
    <div className="app">
      <nav className="nav">
        <Link to="/" className="iconbtn" title="Back to console" aria-label="Back to console">
          <HomeIcon />
        </Link>
        <span className="brand">⚙ Settings</span>
      </nav>
      <div className="container">
        <SettingsPage />
      </div>
    </div>
  );
}
