import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { ConfigProvider } from "./config/ConfigContext";
import { UsersProvider } from "./users/UsersContext";
import { DockProvider } from "./dock/DockContext";
import Layout from "./components/Layout";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import PendingApprovalPage from "./pages/PendingApprovalPage";
import DashboardPage from "./pages/DashboardPage";
import ItemsPage from "./pages/ItemsPage";
import ArchivePage from "./pages/ArchivePage";
import SettingsPage from "./pages/SettingsPage";
import AdminPage from "./pages/AdminPage";
import { QuickWinsPage, AttentionPage, TeamPage } from "./pages/RailPages";

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
        <DockProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/quick-wins" element={<QuickWinsPage />} />
              <Route path="/attention" element={<AttentionPage />} />
              <Route path="/team" element={<TeamPage />} />
              <Route path="/items" element={<ItemsPage />} />
              <Route path="/archive" element={<ArchivePage />} />
              <Route path="/settings" element={<SettingsPage />} />
              <Route
                path="/admin"
                element={user.role === "ADMIN" ? <AdminPage /> : <Navigate to="/" replace />}
              />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </DockProvider>
      </UsersProvider>
    </ConfigProvider>
  );
}
