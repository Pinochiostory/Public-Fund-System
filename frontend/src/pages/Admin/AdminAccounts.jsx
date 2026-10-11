import ActionFeedback from "../../components/ActionFeedback";
import { useRef, useState } from "react";
import AppShell from "../../components/AppShell";
import PageHeader from "../../components/PageHeader";
import api from "../../services/api";

const emptyForm = { fullName: "", email: "", password: "", confirmPassword: "", role: "GOVERNMENT_OFFICER" };

export default function AdminAccounts() {
  const [form, setForm] = useState(emptyForm);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const submitting = useRef(false);
  const change = event => setForm(previous => ({ ...previous, [event.target.name]: event.target.value }));

  async function submit(event) {
    event.preventDefault();
    if (submitting.current) return;
    setError("");
    setSuccess("");
    if (form.password !== form.confirmPassword) {
      setError("Passwords do not match.");
      return;
    }
    submitting.current = true;
    setBusy(true);
    try {
      const response = await api.post("/auth/staff", new URLSearchParams({
        fullName: form.fullName.trim(), email: form.email.trim(),
        password: form.password, role: form.role,
      }));
      const account = response.data;
      setSuccess(`${account.fullName}'s ${account.role === "ADMIN" ? "administrator" : "government officer"} account has been created. They can sign in using the existing login page. Share their credentials securely; no email is sent automatically.`);
      setForm(emptyForm);
    } catch (failure) {
      const status = failure.response?.status;
      const message = failure.response?.data;
      setError(status === 401 || status === 403
        ? "An active administrator session is required. Please sign in again."
        : typeof message === "string" && message.length < 300
          ? message : "Unable to create the account. Please try again.");
      setForm(previous => ({ ...previous, password: "", confirmPassword: "" }));
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  }

  return <AppShell><main className="admin-main">
    <PageHeader title="Staff accounts" description="Create an account for a government officer or an additional administrator." />
    <form className="application-form" onSubmit={submit} aria-busy={busy}>
      <div className="application-row">
        <div className="application-field">
          <label htmlFor="staff-name">Full name</label>
          <input id="staff-name" name="fullName" value={form.fullName} onChange={change} maxLength={255} autoComplete="off" disabled={busy} required />
        </div>
        <div className="application-field">
          <label htmlFor="staff-email">Email address</label>
          <input id="staff-email" name="email" type="email" value={form.email} onChange={change} maxLength={255} autoComplete="off" disabled={busy} required />
        </div>
      </div>
      <div className="application-field">
        <label htmlFor="staff-role">Account role</label>
        <select id="staff-role" name="role" value={form.role} onChange={change} disabled={busy} aria-describedby="staff-permissions">
          <option value="GOVERNMENT_OFFICER">Government Officer</option>
          <option value="ADMIN">Administrator</option>
        </select>
        <p id="staff-permissions">{form.role === "ADMIN"
          ? "Administrators can manage funding, payments and create other staff accounts."
          : "Government officers review project evidence and view project phases."}</p>
      </div>
      <div className="application-row">
        <div className="application-field">
          <label htmlFor="staff-password">Password</label>
          <input id="staff-password" name="password" type="password" value={form.password} onChange={change} minLength={8} autoComplete="new-password" aria-describedby="staff-password-help" disabled={busy} required />
        </div>
        <div className="application-field">
          <label htmlFor="staff-confirm">Confirm password</label>
          <input id="staff-confirm" name="confirmPassword" type="password" value={form.confirmPassword} onChange={change} minLength={8} autoComplete="new-password" disabled={busy} required />
        </div>
      </div>
      <p id="staff-password-help">Use at least 8 characters, including uppercase and lowercase letters, a number and a special character.</p>
      {error && <div className="application-error" role="alert">{error}</div>}
      {success && <ActionFeedback message={success}/>}
      <div className="application-actions">
        <button className="submit-application-button" type="submit" disabled={busy}>
          {busy && <span className="button-spinner" aria-hidden="true" />}
          {busy ? "Creating account…" : "Create account"}
        </button>
      </div>
    </form>
  </main></AppShell>;
}
