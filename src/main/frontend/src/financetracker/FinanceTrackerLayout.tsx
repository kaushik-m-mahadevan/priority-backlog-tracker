import { NavLink, Outlet } from "react-router-dom";
import AppHeader from "../components/AppHeader";
import FinanceGroupsPage from "./pages/FinanceGroupsPage";
import { useFinanceGroup } from "./FinanceGroupContext";

export default function FinanceTrackerLayout() {
  const { loading, currentGroupId } = useFinanceGroup();

  return (
    <div className="app">
      <AppHeader
        appletIcon="💰"
        appletName="Finance Tracker"
        appletHref="/financetracker"
        navLinks={
          currentGroupId && (
            <div className="nav-links">
              <NavLink to="/financetracker" end>
                Overview
              </NavLink>
              <NavLink to="/financetracker/expenses">Expenses</NavLink>
              <NavLink to="/financetracker/income">Income</NavLink>
              <NavLink to="/financetracker/profit-split">Profit split</NavLink>
              <NavLink to="/financetracker/manage">Manage</NavLink>
            </div>
          )
        }
        extraMenuLinks={[{ to: "/financetracker/groups", label: "Switch finance group" }]}
        groupId={currentGroupId}
      />

      <div className="container">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : !currentGroupId ? (
          <FinanceGroupsPage />
        ) : (
          <Outlet />
        )}
      </div>

      {currentGroupId && (
        <nav className="tabbar text">
          <NavLink to="/financetracker" end>
            Overview
          </NavLink>
          <NavLink to="/financetracker/expenses">Expenses</NavLink>
          <NavLink to="/financetracker/income">Income</NavLink>
          <NavLink to="/financetracker/profit-split">Split</NavLink>
          <NavLink to="/financetracker/manage">Manage</NavLink>
        </nav>
      )}
    </div>
  );
}
