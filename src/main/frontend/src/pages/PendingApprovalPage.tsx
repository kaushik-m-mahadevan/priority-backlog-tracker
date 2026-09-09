import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";

/** Shown to a PENDING account. Ambient growing tree + a vague reassurance; polls /me
 *  so the app opens on its own the moment an admin approves. Doesn't name the admin. */
export default function PendingApprovalPage() {
  const { refresh, logout } = useAuth();
  const [stage, setStage] = useState(0);

  useEffect(() => {
    const grow = setInterval(() => setStage((s) => (s + 1) % 6), 1400);
    const poll = setInterval(() => {
      void refresh();
    }, 15000);
    return () => {
      clearInterval(grow);
      clearInterval(poll);
    };
  }, [refresh]);

  return (
    <div className="login-wrap">
      <div className="login-card" style={{ textAlign: "center" }}>
        <svg width="150" height="150" viewBox="0 0 150 150" aria-hidden="true">
          <path d="M25 130 H125" stroke="var(--border)" strokeWidth="2" strokeLinecap="round" />
          <path
            d={`M75 130 V${118 - stage * 9}`}
            stroke="var(--text-dim)"
            strokeWidth={3 + stage * 0.7}
            strokeLinecap="round"
            style={{ transition: "all 1s ease" }}
          />
          <g fill="var(--growth)" style={{ transition: "all 1s ease" }} opacity="0.9">
            <circle cx="75" cy={104 - stage * 8} r={6 + stage * 6} />
            {stage >= 2 && <circle cx={60 - stage} cy={110 - stage * 5} r={5 + stage * 3} />}
            {stage >= 2 && <circle cx={90 + stage} cy={110 - stage * 5} r={5 + stage * 3} />}
            {stage >= 4 && <circle cx="75" cy={86 - stage * 5} r={5 + stage * 2} />}
          </g>
        </svg>
        <h1 className="page-title" style={{ marginTop: 8 }}>
          Thanks for signing up
        </h1>
        <p className="page-sub">
          Your account is being set up. We’ll get back to you soon — this page will open
          on its own once you’re in.
        </p>
        <button className="ghost" onClick={logout} style={{ marginTop: 8 }}>
          Sign out
        </button>
      </div>
    </div>
  );
}
