import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import AuthLayout from "../components/AuthLayout";
import { useAuth } from "../context/AuthContext";
import api from "../services/api";

export default function RequiredPasswordChange() {
  const { user, isAuthenticated, login, logout } = useAuth();
  const navigate = useNavigate();
  const [temporaryPassword, setTemporaryPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const dashboard = user?.role === "ADMINISTRATOR" ? "/admin" : user?.role === "VERIFICATION_OFFICER" ? "/officer" : "/provider";

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!user.mustChangePassword) return <Navigate to={dashboard} replace />;

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setError("");
    if (newPassword !== confirmPassword) { setError("Passwords do not match."); return; }
    if (newPassword === temporaryPassword) { setError("Choose a password different from the temporary password."); return; }
    setBusy(true);
    try {
      const response = await api.post("/auth/change-password", new URLSearchParams({ temporaryPassword, newPassword }));
      setTemporaryPassword(""); setNewPassword(""); setConfirmPassword("");
      login(response.data.user, response.data.token);
      navigate(dashboard, { replace: true });
    } catch (failure) {
      const message = failure.response?.data?.message || failure.response?.data;
      setError(typeof message === "string" && message.length < 400 ? message : "Unable to change your password. Please sign in again if your session expired.");
    } finally { setBusy(false); }
  }

  return <AuthLayout title="Make this account yours." description="Replace the temporary password to securely continue to your workspace.">
    <div className="recovery-content">
      <h1>Choose your own password</h1>
      <p className="recovery-description">Your administrator reset your password. You must change it before accessing your dashboard.</p>
      <form className="recovery-form" onSubmit={submit} aria-busy={busy}>
        <label htmlFor="temporary-password">Temporary password</label>
        <input id="temporary-password" type="password" autoComplete="current-password" value={temporaryPassword} onChange={event => setTemporaryPassword(event.target.value)} disabled={busy} required />
        <label htmlFor="required-new-password">New password</label>
        <input id="required-new-password" type="password" autoComplete="new-password" value={newPassword} onChange={event => setNewPassword(event.target.value)} minLength={8} aria-describedby="required-password-help" disabled={busy} required />
        <p id="required-password-help">At least 8 characters, including uppercase, lowercase, a number and a special character.</p>
        <label htmlFor="required-confirm-password">Confirm new password</label>
        <input id="required-confirm-password" type="password" autoComplete="new-password" value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)} minLength={8} disabled={busy} required />
        {error && <div className="recovery-alert error" role="alert">{error}</div>}
        <button className="recovery-primary" disabled={busy} type="submit">{busy ? "Updating password…" : "Save password and continue"}</button>
        <button type="button" disabled={busy} onClick={() => { logout(); navigate("/login", { replace: true }); }}>Sign out</button>
      </form>
    </div>
  </AuthLayout>;
}
