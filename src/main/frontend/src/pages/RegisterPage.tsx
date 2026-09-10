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
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await register({ name: name.trim(), handle: handle.trim().toLowerCase(), email: email.trim(), password });
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
              <label>Name</label>
              <input value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
            </div>
            <div className="form-row">
              <label>Handle</label>
              <input
                value={handle}
                onChange={(e) => setHandle(e.target.value)}
                placeholder="lowercase, 1–30 chars, letters/digits/-/_"
                pattern="[a-z0-9_-]{1,30}"
                required
              />
            </div>
            <div className="form-row">
              <label>Email</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="username"
                required
              />
            </div>
            <div className="form-row">
              <label>Password</label>
              <PasswordInput
                value={password}
                onChange={setPassword}
                autoComplete="new-password"
                required
              />
              <div className="hint">At least 8 characters.</div>
            </div>
            <button type="submit" className="primary" style={{ width: "100%" }} disabled={busy}>
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
