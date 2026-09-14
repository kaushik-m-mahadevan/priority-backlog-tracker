import AppHeader from "../components/AppHeader";
import AdminPage from "./AdminPage";

/**
 * Admin Console as its own applet (design: platform integration follow-up) — user
 * administration is platform-wide, not Backlog-Tracker-specific, so it gets its own
 * top-level route and launcher card instead of living inside Backlog Tracker's chrome.
 */
export default function AdminShell() {
  return (
    <div className="app">
      <AppHeader appletIcon="◇" appletName="Admin Console" />
      <div className="container">
        <AdminPage />
      </div>
    </div>
  );
}
