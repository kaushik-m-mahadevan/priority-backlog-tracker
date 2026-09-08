import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";

export default function LoginPage() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("test123");
  const [password, setPassword] = useState("test123");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      nav("/", { replace: true });
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 401
          ? "Invalid email or password"
          : "Sign in failed",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <div className="login-card">
        <h1 className="page-title" style={{ textAlign: "center" }}>
          ◆ Backlog Tracker
        </h1>
        <p className="page-sub" style={{ textAlign: "center" }}>
          Sign in to continue
        </p>
        <div className="card">
          {error && <div className="error">{error}</div>}
          <form onSubmit={submit}>
            <div className="form-row">
              <label>Email</label>
              <input value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
            </div>
            <div className="form-row">
              <label>Password</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <button type="submit" className="primary" style={{ width: "100%" }} disabled={busy}>
              {busy ? "Signing in…" : "Sign in"}
            </button>
          </form>
        </div>
        <p className="page-sub" style={{ textAlign: "center", marginTop: 12 }}>
          Dev account is pre-filled.
        </p>
      </div>
    </div>
  );
}
