import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";
import PasswordInput from "../components/PasswordInput";

export default function RegisterPage() {
  const { register } = useAuth();
  const [name, setName] = useState("");
  const [handle, setHandle] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mismatch = confirm.length > 0 && confirm !== password;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setError("Passwords don't match");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await register({
        name: name.trim(),
        handle: handle.trim().toLowerCase(),
        email: email.trim(),
        password,
        confirmPassword: confirm,
      });
      // AuthContext now holds a PENDING user → App routes to the waiting screen
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not create your account");
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
          Create your account
        </p>
        <div className="card">
          {error && <div className="error">{error}</div>}
          <form onSubmit={submit}>
            <div className="form-row">
              <label htmlFor="register-name">Name</label>
              <input id="register-name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
            </div>
            <div className="form-row">
              <label htmlFor="register-handle">Handle</label>
              <input
                id="register-handle"
                value={handle}
                onChange={(e) => setHandle(e.target.value)}
                placeholder="lowercase, 1–30 chars, letters/digits/-/_"
                pattern="[a-z0-9_-]{1,30}"
                required
              />
            </div>
            <div className="form-row">
              <label htmlFor="register-email">Email</label>
              <input
                id="register-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="username"
                required
              />
            </div>
            <div className="form-row">
              <label htmlFor="register-password">Password</label>
              <PasswordInput
                id="register-password"
                value={password}
                onChange={setPassword}
                autoComplete="new-password"
                required
              />
              <div className="hint">At least 8 characters.</div>
            </div>
            <div className="form-row">
              <label htmlFor="register-confirm-password">Confirm password</label>
              <PasswordInput
                id="register-confirm-password"
                value={confirm}
                onChange={setConfirm}
                autoComplete="new-password"
                required
              />
              {mismatch && <div className="hint bad">Passwords don't match.</div>}
            </div>
            <button
              type="submit"
              className="primary"
              style={{ width: "100%" }}
              disabled={busy || mismatch || !password}
            >
              {busy ? "Creating…" : "Create account"}
            </button>
          </form>
        </div>
        <p className="page-sub" style={{ textAlign: "center", marginTop: 12 }}>
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  );
}
