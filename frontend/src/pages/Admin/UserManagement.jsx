import ActionFeedback from "../../components/ActionFeedback";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import AppShell from "../../components/AppShell";
import PageHeader from "../../components/PageHeader";
import LoadingSkeleton from "../../components/LoadingSkeleton";
import ModalFrame from "../../components/ModalFrame";
import Icon from "../../components/Icon";
import { useAuth } from "../../context/AuthContext";
import api from "../../services/api";

const roleLabels = { ADMIN: "Administrator", GOVERNMENT_OFFICER: "Government Officer", SERVICE_PROVIDER: "Service Provider" };
const errorMessage = (error, fallback) => {
  const text = error.response?.data?.message || error.response?.data;
  return typeof text === "string" && text.length < 400 ? text : fallback;
};

export default function UserManagement() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [query, setQuery] = useState("");
  const [role, setRole] = useState("");
  const [status, setStatus] = useState("");
  const [confirmation, setConfirmation] = useState(null);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState("");
  const [resetResult, setResetResult] = useState(null);
  const submitting = useRef(false);

  const loadUsers = useCallback(async (signal) => {
    setLoading(true);
    setError("");
    try {
      const response = await api.get("/admin/users", { signal });
      setUsers(response.data);
    } catch (failure) {
      if (!signal?.aborted) setError(errorMessage(failure, "Unable to load users. Please try again."));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    loadUsers(controller.signal);
    return () => controller.abort();
  }, [loadUsers]);

  const filtered = useMemo(() => users.filter(user => {
    const term = query.trim().toLowerCase();
    return (!term || user.fullName.toLowerCase().includes(term) || user.email.toLowerCase().includes(term))
      && (!role || user.role === role) && (!status || user.accountStatus === status);
  }), [users, query, role, status]);

  const confirm = (user, action) => {
    setSuccess(""); setDialogError(""); setConfirmation({ user, action });
  };
  async function performAction() {
    if (submitting.current || !confirmation) return;
    submitting.current = true;
    setBusy(true); setDialogError("");
    const { user, action } = confirmation;
    try {
      const response = action === "reset"
        ? await api.post(`/admin/users/${user.id}/reset-password`)
        : await api.patch(`/admin/users/${user.id}/status`, { status: action });
      const updated = action === "reset" ? response.data.user : response.data;
      setUsers(previous => previous.map(item => item.id === updated.id ? updated : item));
      setConfirmation(null);
      if (action === "reset") {
        // Kept only in this component's memory; closing/unmounting discards it.
        setResetResult(response.data);
      } else {
        setSuccess(`${updated.fullName}'s account has been ${action === "ACTIVE" ? "enabled" : "disabled"}.`);
      }
    } catch (failure) {
      setDialogError(errorMessage(failure, "The action could not be completed. Please try again."));
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  }

  return <AppShell><main className="admin-main">
    <PageHeader title="User Management" description="Manage account access and initiate password resets.">
      <Link className="project-action" to="/admin/accounts"><Icon name="plus" size={18} />Create staff account</Link>
    </PageHeader>
    {error && <div className="admin-error" role="alert">{error} <button onClick={() => loadUsers()}>Try again</button></div>}
    {success && <ActionFeedback message={success}/>}
    {loading ? <LoadingSkeleton cards={2} /> : <>
      <section className="record-tools" aria-label="Filter users">
        <div className="record-controls">
          <label className="record-search"><Icon name="search" size={18} /><input aria-label="Search users" placeholder="Search by name or email…" value={query} onChange={event => setQuery(event.target.value)} /></label>
          <select aria-label="Filter by role" value={role} onChange={event => setRole(event.target.value)}>
            <option value="">All roles</option>{Object.entries(roleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
          <select aria-label="Filter by status" value={status} onChange={event => setStatus(event.target.value)}>
            <option value="">All statuses</option>{[...new Set(["ACTIVE", "DISABLED", "LOCKED", ...users.map(item => item.accountStatus)])].map(value => <option key={value} value={value}>{value}</option>)}
          </select>
          {(query || role || status) && <button onClick={() => { setQuery(""); setRole(""); setStatus(""); }}>Clear filters</button>}
        </div>
        <p role="status">{filtered.length} of {users.length} users</p>
      </section>
      <section className="admin-card">
        <div className="table-wrapper" tabIndex={0} role="region" aria-label="User accounts">
          <table><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>{filtered.map(user => {
              const self = String(user.id) === String(currentUser?.id);
              const canChangeStatus = !self && ["SERVICE_PROVIDER", "GOVERNMENT_OFFICER"].includes(user.role);
              return <tr key={user.id}>
                <td><strong>{user.fullName}{self && " (You)"}</strong>{user.mustChangePassword && <span>Password change required</span>}</td>
                <td>{user.email}</td><td>{roleLabels[user.role] || user.role}</td>
                <td><span className={"status-pill " + (user.accountStatus === "ACTIVE" ? "status-approved" : user.accountStatus === "DISABLED" ? "status-rejected" : "status-pending")}>{user.accountStatus}</span></td>
                <td><div className="action-buttons">
                  {canChangeStatus && <button onClick={() => confirm(user, user.accountStatus === "DISABLED" || user.accountStatus === "LOCKED" ? "ACTIVE" : "DISABLED")}>
                    {user.accountStatus === "DISABLED" || user.accountStatus === "LOCKED" ? "Enable" : "Disable"}
                  </button>}
                  {!self && <button onClick={() => confirm(user, "reset")}>Reset password</button>}
                  {self && <span>Your current account</span>}
                </div></td>
              </tr>;
            })}</tbody>
          </table>
        </div>
        {filtered.length === 0 && <div className="empty-state">{users.length ? "No users match your filters." : "No users found."}</div>}
      </section>
    </>}
    {confirmation && <ModalFrame className="user-management-dialog" busy={busy} onClose={() => setConfirmation(null)} error={dialogError}>
      <div className="account-modal-header"><div><h2>{confirmation.action === "reset" ? "Reset this password?" : confirmation.action === "ACTIVE" ? "Enable this account?" : "Disable this account?"}</h2></div></div>
      <div className="account-modal-summary"><strong>{confirmation.user.fullName}</strong><span>{confirmation.user.email}</span></div>
      <p>{confirmation.action === "reset"
        ? "A new temporary password will replace their current password and end their existing sessions. They must choose a new password at their next login. Disabled accounts will remain disabled."
        : confirmation.action === "DISABLED" ? "Are you sure you want to disable this account? The user will not be able to sign in or access protected functionality."
          : "This user will be able to sign in again. Any required password change will still apply."}</p>
      <div className="account-modal-actions"><button disabled={busy} onClick={() => setConfirmation(null)}>Cancel</button>
        <button className="account-primary" disabled={busy} aria-busy={busy} onClick={performAction}>{busy ? "Working…" : confirmation.action === "reset" ? "Reset password" : confirmation.action === "ACTIVE" ? "Enable account" : "Disable account"}</button>
      </div>
    </ModalFrame>}
    {resetResult && <ModalFrame className="user-management-dialog" onClose={() => setResetResult(null)}>
      <div className="account-modal-header"><div><h2>Temporary password created</h2></div></div>
      <p>Share this securely with {resetResult.user.fullName} ({resetResult.user.email}). It is shown only now and cannot be retrieved after you close this dialog.</p>
      <label>Temporary password<input readOnly value={resetResult.temporaryPassword} autoComplete="off" spellCheck={false} onFocus={event => event.target.select()} /></label>
      <p>They must replace it after signing in. {resetResult.user.accountStatus === "DISABLED" && "The account is still disabled; enable it before they can sign in."}</p>
      <div className="account-modal-actions"><button className="account-primary" onClick={() => setResetResult(null)}>Done — close securely</button></div>
    </ModalFrame>}
  </main></AppShell>;
}
