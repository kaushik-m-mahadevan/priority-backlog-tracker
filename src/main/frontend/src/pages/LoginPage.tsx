import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import PasswordInput from "../components/PasswordInput";

export default function LoginPage() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [forgot, setForgot] = useState(false);
  const [forgotSent, setForgotSent] = useState(false);

  async function sendForgot(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await api.post("/auth/forgot-password", { email: email.trim() });
    } catch {
      /* deliberately silent — never reveal whether the account exists */
    } finally {
      setBusy(false);
      setForgotSent(true);
    }
  }

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

          {forgot ? (
            forgotSent ? (
              <div>
                <p className="page-sub">
                  If that account exists, an admin has been notified and will set a
                  temporary password for you to collect from them.
                </p>
                <button className="ghost" onClick={() => { setForgot(false); setForgotSent(false); }}>
                  Back to sign in
                </button>
              </div>
            ) : (
              <form onSubmit={sendForgot}>
                <div className="form-row">
                  <label htmlFor="forgot-email">Email</label>
                  <input
                    id="forgot-email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    autoFocus
                  />
                </div>
                <button type="submit" className="primary" style={{ width: "100%" }} disabled={busy || !email.trim()}>
                  {busy ? "Sending…" : "Request a password reset"}
                </button>
                <button
                  type="button"
                  className="linkbtn"
                  style={{ marginTop: 10 }}
                  onClick={() => setForgot(false)}
                >
                  Back to sign in
                </button>
              </form>
            )
          ) : (
          <form onSubmit={submit}>
            <div className="form-row">
              <label htmlFor="login-email">Email</label>
              {/* plain text, not type=email: legacy accounts may have a non-email login id */}
              <input
                id="login-email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="username"
                required
                autoFocus
              />
            </div>
            <div className="form-row">
              <label htmlFor="login-password">Password</label>
              <PasswordInput
                id="login-password"
                value={password}
                onChange={setPassword}
                autoComplete="current-password"
                required
              />
            </div>
            <button type="submit" className="primary" style={{ width: "100%" }} disabled={busy}>
              {busy ? "Signing in…" : "Sign in"}
            </button>
            <button
              type="button"
              className="linkbtn"
              style={{ marginTop: 10 }}
              onClick={() => setForgot(true)}
            >
              Forgot password?
            </button>
          </form>
          )}
        </div>
        <p className="page-sub" style={{ textAlign: "center", marginTop: 12 }}>
          New here? <Link to="/register">Create an account</Link>
        </p>
      </div>
    </div>
  );
}
