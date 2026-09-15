import { useFinanceGroup } from "../FinanceGroupContext";

/** Placeholder landing page — the real ledger (expenses, income, balances, profit
 *  distribution) lands as its own atomic features next. This exists now so the applet
 *  shell (header, group switcher, bottom tabbar) has something real to route to and be
 *  verified against on both mobile and desktop. */
export default function OverviewPage() {
  const { currentFinanceGroup } = useFinanceGroup();

  return (
    <div>
      <h1 className="page-title">Overview</h1>
      <p className="page-sub">{currentFinanceGroup?.name}</p>
      <div className="card">
        <p className="empty">Expenses, income, and balances are coming here next.</p>
      </div>
    </div>
  );
}
