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
import SettingsShell from "./pages/SettingsShell";
import AdminShell from "./pages/AdminShell";
import GroupsPage from "./pages/GroupsPage";
import { QuickWinsPage, AttentionPage, TeamPage } from "./pages/RailPages";
import OrderTrackerRoot from "./ordertracker/OrderTrackerRoot";
import CustomersPage from "./ordertracker/pages/CustomersPage";
import OrdersPage from "./ordertracker/pages/OrdersPage";
import MyWorkPage from "./ordertracker/pages/MyWorkPage";
import NewOrderPage from "./ordertracker/pages/NewOrderPage";
import OrderDetailPage from "./ordertracker/pages/OrderDetailPage";
import BusinessSettingsPage from "./ordertracker/pages/BusinessSettingsPage";
import ManageBusinessPage from "./ordertracker/pages/ManageBusinessPage";
import FinanceTrackerRoot from "./financetracker/FinanceTrackerRoot";
import OverviewPage from "./financetracker/pages/OverviewPage";
import ExpensesPage from "./financetracker/pages/ExpensesPage";

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
                </Route>
                {/* Settings and Admin Console are their own top-level shells (design:
                    platform integration follow-up) — both are platform-level, not
                    Backlog-Tracker-specific, so neither renders inside Layout/nav/tabbar. */}
                <Route path="/settings" element={<SettingsShell />} />
                <Route
                  path="/admin"
                  element={user.role === "ADMIN" ? <AdminShell /> : <Navigate to="/" replace />}
                />
                <Route path="/ordertracker" element={<OrderTrackerRoot />}>
                  <Route index element={<Navigate to="orders" replace />} />
                  <Route path="orders" element={<OrdersPage />} />
                  <Route path="my-work" element={<MyWorkPage />} />
                  <Route path="orders/new" element={<NewOrderPage />} />
                  <Route path="orders/:orderId" element={<OrderDetailPage />} />
                  <Route path="customers" element={<CustomersPage />} />
                  <Route path="manage-business" element={<ManageBusinessPage />} />
                  <Route path="business-settings" element={<BusinessSettingsPage />} />
                </Route>
                <Route path="/financetracker" element={<FinanceTrackerRoot />}>
                  <Route index element={<OverviewPage />} />
                  <Route path="expenses" element={<ExpensesPage />} />
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
