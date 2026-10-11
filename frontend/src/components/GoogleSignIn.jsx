import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../services/api";
import { useAuth } from "../context/AuthContext";
import ModalFrame from "./ModalFrame";
import "./GoogleSignIn.css";

let googleScript;
function loadGoogle() {
  if (window.google?.accounts?.id) return Promise.resolve(window.google);
  if (!googleScript) googleScript = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.onload = () => resolve(window.google);
    script.onerror = () => { script.remove(); googleScript = null; reject(new Error("Google sign-in could not load. Check your connection or use email and password.")); };
    document.head.appendChild(script);
  });
  return googleScript;
}

export default function GoogleSignIn({ disabled = false }) {
  const container = useRef(null);
  const { login } = useAuth();
  const navigate = useNavigate();
  const [attempt, setAttempt] = useState(0);
  const [ready, setReady] = useState(false);
  const [configured, setConfigured] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [pending, setPending] = useState(null);
  const [password, setPassword] = useState("");
  const inFlight = useRef(false);

  const submit = useCallback(async (credential, nonce, existingPassword = "") => {
    if (inFlight.current) return;
    inFlight.current = true; setBusy(true); setError("");
    try {
      const { data } = await api.post("/auth/google/login", new URLSearchParams({ credential, nonce, existingPassword }), { withCredentials: true });
      setPending(null); setPassword("");
      login(data.user, data.token);
      navigate(data.user.mustChangePassword ? "/change-password" : "/provider", { replace: true });
    } catch (failure) {
      const data = failure.response?.data;
      if (data?.code === "GOOGLE_LINK_REQUIRED") setPending({ credential, nonce });
      else {
        setPending(null); setPassword(""); setReady(false);
        setError(typeof data === "string" ? data : typeof data?.message === "string" ? data.message : "Google sign-in failed. Try again or use email and password.");
      }
    } finally { inFlight.current = false; setBusy(false); }
  }, [login, navigate]);

  const retry = () => { setReady(false); setError(""); setPending(null); setPassword(""); setAttempt(value => value + 1); };

  useEffect(() => {
    let cancelled = false;
    const node = container.current;
    (async () => {
      try {
        const { data } = await api.get("/auth/google/config");
        if (cancelled) return;
        if (!data.clientId) { setConfigured(false); return; }
        setConfigured(true);
        const google = await loadGoogle();
        if (cancelled) return;
        const challenge = await api.post("/auth/google/challenge", null, { withCredentials: true });
        if (cancelled) return;
        google.accounts.id.initialize({
          client_id: data.clientId, nonce: challenge.data.nonce, auto_select: false,
          callback: response => { if (!cancelled) submit(response.credential, challenge.data.nonce); },
        });
        node.replaceChildren();
        google.accounts.id.renderButton(node, { type: "standard", theme: "outline", size: "large", text: "continue_with", shape: "pill", width: Math.min(360, node.clientWidth || 300) });
        setReady(true);
      } catch (failure) {
        if (!cancelled) setError((typeof failure.response?.data === "string" ? failure.response.data : failure.response?.data?.message) || failure.message || "Google sign-in is unavailable. Use email and password.");
      }
    })();
    return () => { cancelled = true; node?.replaceChildren(); };
  }, [attempt, submit]);

  if (!configured) return null;
  return <section className="google-sign-in" aria-label="Google sign-in">
    <div className="google-divider"><span>or</span></div>
    <div ref={container} className={busy || disabled || !ready ? "google-button-host is-disabled" : "google-button-host"} inert={busy || disabled || !ready ? true : undefined} />
    {!ready && !error && <p role="status">Loading Google sign-in…</p>}
    {busy && <p role="status">Checking your Google account…</p>}
    {error && <><p className="login-error" role="alert">{error}</p><button type="button" className="recovery-text-button" onClick={retry}>Retry Google sign-in</button></>}
    {pending && <ModalFrame className="google-link-modal" busy={busy} onClose={retry}>
      <form onSubmit={event => { event.preventDefault(); submit(pending.credential, pending.nonce, password); }}>
        <h2>Connect your existing account</h2>
        <p>Confirm your current Public Fund password. This keeps your existing projects and prevents someone else linking your account.</p>
        <label htmlFor="google-link-password">Current Public Fund password</label>
        <div className="auth-field"><input id="google-link-password" type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} required /></div>
        <button className="login-button" type="submit" disabled={busy}>{busy ? "Connecting…" : "Confirm and connect Google"}</button>
        <button className="recovery-text-button" type="button" disabled={busy} onClick={retry}>Cancel</button>
      </form>
    </ModalFrame>}
  </section>;
}

