import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { ConfigProvider } from "./config/ConfigContext";
import { UsersProvider } from "./users/UsersContext";
import Layout from "./components/Layout";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import ItemsPage from "./pages/ItemsPage";
import ArchivePage from "./pages/ArchivePage";
import SettingsPage from "./pages/SettingsPage";
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
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return (
    <ConfigProvider>
      <UsersProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/quick-wins" element={<QuickWinsPage />} />
          <Route path="/attention" element={<AttentionPage />} />
          <Route path="/team" element={<TeamPage />} />
          <Route path="/items" element={<ItemsPage />} />
          <Route path="/archive" element={<ArchivePage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      </UsersProvider>
    </ConfigProvider>
  );
}
