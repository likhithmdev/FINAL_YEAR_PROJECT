import React, { useState } from "react";
import { AlertTriangle, Radio, ShieldCheck, Siren } from "lucide-react";
import { describeAuthError, signInOperator } from "../integrations/firebaseClient";

// The console's data lives behind Realtime Database rules that require an
// authenticated session, so this is the first thing an operator sees. Anyone
// can still open the offline demo (`?demo=1`), which needs no credentials.
export default function LoginGate() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      // On success Firebase emits an auth state change and the console replaces
      // this screen, so there is nothing to navigate here.
      await signInOperator(email.trim(), password);
    } catch (signInError) {
      setError(describeAuthError(signInError));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-shell">
      <form className="card login-card" onSubmit={handleSubmit}>
        <div className="brand">
          <div className="brand-badge">
            <Siren size={22} />
          </div>
          <div>
            <div className="brand-title">SAPTCS</div>
            <div className="brand-sub">Control Room</div>
          </div>
        </div>

        <h1 className="login-heading">Operator sign-in</h1>
        <p className="login-sub">
          Live traffic, junction and hospital data requires an authenticated
          session. Authorised control-room staff only.
        </p>

        <label className="field">
          <span>Email</span>
          <input
            type="email"
            className="input"
            autoComplete="username"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="operator@example.com"
            required
          />
        </label>

        <label className="field">
          <span>Password</span>
          <input
            type="password"
            className="input"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </label>

        {error ? (
          <div className="login-error" role="alert">
            <AlertTriangle size={15} />
            <span>{error}</span>
          </div>
        ) : null}

        <button type="submit" className="btn btn-primary login-submit" disabled={busy}>
          <ShieldCheck size={16} />
          {busy ? "Signing in…" : "Sign in"}
        </button>

        <div className="login-alt">
          <a className="login-demo-link" href="?demo=1">
            <Radio size={14} />
            Open the offline demo instead
          </a>
          <span className="dim">No account or network needed.</span>
        </div>
      </form>
    </div>
  );
}
