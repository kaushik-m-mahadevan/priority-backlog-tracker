import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { ConfigProvider } from "./config/ConfigContext";
import { UsersProvider } from "./users/UsersContext";
import { GroupProvider } from "./groups/GroupContext";
import { GroupCategoriesProvider } from "./groups/GroupCategoriesContext";
import { GroveSettingsProvider } from "./grove/GroveSettingsContext";
import { DockProvider } from "./dock/DockContext";
import Layout from "./components/Layout";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import PendingApprovalPage from "./pages/PendingApprovalPage";
import LauncherPage from "./pages/LauncherPage";
import DashboardPage from "./pages/DashboardPage";
import ItemsPage from "./pages/ItemsPage";
import ArchivePage from "./pages/ArchivePage";
import SettingsPage from "./pages/SettingsPage";
import AdminShell from "./pages/AdminShell";
import GroupsPage from "./pages/GroupsPage";
import { QuickWinsPage, AttentionPage, TeamPage } from "./pages/RailPages";
import OrderTrackerRoot from "./ordertracker/OrderTrackerRoot";
import CustomersPage from "./ordertracker/pages/CustomersPage";
import OrdersPage from "./ordertracker/pages/OrdersPage";
import BulkOrdersPage from "./ordertracker/pages/BulkOrdersPage";
import BusinessSettingsPage from "./ordertracker/pages/BusinessSettingsPage";

export default function App() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="login-wrap">
        <p className="muted">Loading…</p>
      </div>
    );
  }

  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  if (user.status !== "ACTIVE") {
    return <PendingApprovalPage />;
  }

  return (
    <ConfigProvider>
      <UsersProvider>
        <GroupProvider>
          <GroupCategoriesProvider>
          <GroveSettingsProvider>
            <DockProvider>
              <Routes>
                {/* Post-login landing page (design: platform integration) — sits outside
                    Layout entirely, since the app-picker isn't Backlog Tracker chrome. */}
                <Route path="/" element={<LauncherPage />} />
                <Route element={<Layout />}>
                  <Route path="/backlog" element={<DashboardPage />} />
                  <Route path="/backlog/quick-wins" element={<QuickWinsPage />} />
                  <Route path="/backlog/attention" element={<AttentionPage />} />
                  <Route path="/backlog/team" element={<TeamPage />} />
                  <Route path="/backlog/items" element={<ItemsPage />} />
                  <Route path="/backlog/archive" element={<ArchivePage />} />
                  <Route path="/backlog/groups" element={<GroupsPage />} />
                  {/* Still rendered with Backlog Tracker's own Layout/nav for now — Phase 4
                      (migrate Settings) is what actually reshapes this into a shared hub. */}
                  <Route path="/settings" element={<SettingsPage />} />
                </Route>
                {/* Admin Console is its own applet (design: platform integration follow-up)
                    — user administration is platform-wide, not Backlog-Tracker-specific,
                    so it sits outside Layout entirely, same as the launcher. */}
                <Route
                  path="/admin"
                  element={user.role === "ADMIN" ? <AdminShell /> : <Navigate to="/" replace />}
                />
                <Route path="/ordertracker" element={<OrderTrackerRoot />}>
                  <Route index element={<Navigate to="orders" replace />} />
                  <Route path="orders" element={<OrdersPage />} />
                  <Route path="bulk-orders" element={<BulkOrdersPage />} />
                  <Route path="customers" element={<CustomersPage />} />
                  <Route path="business-settings" element={<BusinessSettingsPage />} />
                </Route>
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </DockProvider>
          </GroveSettingsProvider>
          </GroupCategoriesProvider>
        </GroupProvider>
      </UsersProvider>
    </ConfigProvider>
  );
}
