import AppHeader from "../components/AppHeader";
import SettingsPage from "./SettingsPage";

/**
 * Settings as its own top-level shell (design: platform integration follow-up) — it's a
 * shared hub (account settings plus each applet's own section), not Backlog-Tracker
 * chrome, so it shouldn't render inside Backlog Tracker's own Layout/nav/tabbar.
 */
export default function SettingsShell() {
  return (
    <div className="app">
      <AppHeader appletIcon="⚙" appletName="Settings" />
      <div className="container">
        <SettingsPage />
      </div>
    </div>
  );
}
