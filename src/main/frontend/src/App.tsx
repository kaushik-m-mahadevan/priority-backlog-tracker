import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { useDisableNumberInputScroll } from "./lib/useDisableNumberInputScroll";
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
import CustomerDetailPage from "./ordertracker/pages/CustomerDetailPage";
import OrdersPage from "./ordertracker/pages/OrdersPage";
import MyWorkPage from "./ordertracker/pages/MyWorkPage";
import NewOrderPage from "./ordertracker/pages/NewOrderPage";
import OrderDetailPage from "./ordertracker/pages/OrderDetailPage";
import BusinessSettingsPage from "./ordertracker/pages/BusinessSettingsPage";
import BusinessesPage from "./ordertracker/pages/BusinessesPage";
import ManageBusinessPage from "./ordertracker/pages/ManageBusinessPage";
import FinanceTrackerRoot from "./financetracker/FinanceTrackerRoot";
import OverviewPage from "./financetracker/pages/OverviewPage";
import LedgerPage from "./financetracker/pages/LedgerPage";
import ProfitSplitPage from "./financetracker/pages/ProfitSplitPage";
import ManageFinanceGroupPage from "./financetracker/pages/ManageFinanceGroupPage";
import FinanceGroupsPage from "./financetracker/pages/FinanceGroupsPage";
import ProductCatalogRoot from "./productcatalog/ProductCatalogRoot";
import ColorwaysPage from "./productcatalog/pages/ColorwaysPage";
import CatalogGroupsPage from "./productcatalog/pages/CatalogGroupsPage";
import ManageCatalogGroupPage from "./productcatalog/pages/ManageCatalogGroupPage";
import MaterialInventoryRoot from "./materialinventory/MaterialInventoryRoot";
import MyInventoryPage from "./materialinventory/pages/MyInventoryPage";
import RequestsPage from "./materialinventory/pages/RequestsPage";
import InventoryGroupsPage from "./materialinventory/pages/InventoryGroupsPage";
import ManageInventoryGroupPage from "./materialinventory/pages/ManageInventoryGroupPage";

export default function App() {
  const { user, loading } = useAuth();
  useDisableNumberInputScroll();

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
                  <Route path="customers/:id" element={<CustomerDetailPage />} />
                  <Route path="manage-business" element={<ManageBusinessPage />} />
                  <Route path="business-settings" element={<BusinessSettingsPage />} />
                  <Route path="businesses" element={<BusinessesPage />} />
                </Route>
                <Route path="/financetracker" element={<FinanceTrackerRoot />}>
                  <Route index element={<OverviewPage />} />
                  <Route path="ledger" element={<LedgerPage />} />
                  <Route path="profit-split" element={<ProfitSplitPage />} />
                  <Route path="manage" element={<ManageFinanceGroupPage />} />
                  <Route path="groups" element={<FinanceGroupsPage />} />
                </Route>
                <Route path="/materialinventory" element={<MaterialInventoryRoot />}>
                  <Route index element={<MyInventoryPage />} />
                  <Route path="requests" element={<RequestsPage />} />
                  <Route path="groups" element={<InventoryGroupsPage />} />
                  <Route path="manage" element={<ManageInventoryGroupPage />} />
                </Route>
                <Route path="/productcatalog" element={<ProductCatalogRoot />}>
                  <Route index element={<ColorwaysPage />} />
                  <Route path="groups" element={<CatalogGroupsPage />} />
                  <Route path="manage" element={<ManageCatalogGroupPage />} />
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
