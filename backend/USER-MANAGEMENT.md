# User Management

Open **Admin → User Management** (`/admin/users`). This uses the existing workspace, table, filters, dialogs, loading components, colours and login system. The existing Staff accounts creation page remains available, with a shortcut from User Management.

## Account actions

- Search by name/email and combine role and status filters.
- View all roles, including existing legacy staff role labels mapped to Admin/Government Officer.
- Enable or disable Service Providers and Government Officers, with confirmation.
- Administrators cannot be disabled here, including the current Admin. The current account is marked **You**, with no self-reset action.
- Reset a Provider, Officer or another Admin after confirmation. A cryptographically random temporary password is returned once, while only its BCrypt hash is stored. Closing the result dialog or leaving the page discards the displayed value; it is not stored in local/session storage. Share it securely; email delivery is not included.
- Resetting a disabled account does not re-enable it. A password-lockout (`LOCKED`) can be recovered through an Admin reset. Enabling a non-admin clears the failed-login counter but retains any required password change.
- The next login with a temporary password opens `/change-password`. The user must enter their temporary password and choose a different password satisfying the existing policy. Only then can they open a normal dashboard.

No user creation date is shown because the existing `users` table has no creation-date column. No fabricated date or extra date field was added.

## Schema and deployment

The only new field is `users.must_change_password BOOLEAN NOT NULL DEFAULT FALSE`. Existing users keep their passwords, roles and statuses, and start with this flag false.

The migration is `database/migrations/001_user_password_change_required.sql`. Apply it once on deployments that do not use the existing Hibernate `ddl-auto=update` configuration. Do not run it again if the column already exists. The local database has been migrated; six existing users were verified with the flag false.

Restart the backend to load the feature. **Existing browser sessions must sign in again once after this update.** JWT sessions are now bound to a keyed fingerprint of the stored password hash and the required-change flag. This lets Admin resets invalidate old sessions immediately without adding a second database field. Passwords and BCrypt hashes are never included in JWTs.

The temporary-password login uses the existing JWT system but grants only a `PASSWORD_CHANGE_REQUIRED` authority with a 15-minute session. Spring Security plus the JWT filter reject normal protected endpoints until the change is complete, even if browser storage or navigation is modified. Completing the change issues a normal session and invalidates the temporary one. Another Admin reset invalidates the previous temporary password/session. The temporary password itself remains usable until replaced; an expired restricted session can be renewed by logging in again.

Disabled accounts cannot log in or use an existing JWT. Public password recovery cannot reactivate disabled accounts, or circumvent an Admin-required password change. Sensitive updates use row locks and transactions so status changes and password operations do not silently overwrite each other.

## API

All `/api/admin/users` endpoints require an active, unrestricted Admin in Spring Security and in the service. The legacy `ADMINISTRATOR` authority remains compatible with database role `ADMIN`.

| Endpoint | Input | Result |
| --- | --- | --- |
| `GET /api/admin/users` | No body | Password-free user DTO array |
| `PATCH /api/admin/users/{id}/status` | JSON `{"status":"ACTIVE"}` or `{"status":"DISABLED"}` | Updated user DTO |
| `POST /api/admin/users/{id}/reset-password` | No body | `{user, temporaryPassword}` with `Cache-Control: no-store` |
| `POST /api/auth/change-password` | Form fields `temporaryPassword`, `newPassword`; restricted login token required | Existing `{user, token}` login response, with `mustChangePassword: false` |

User DTOs contain ID, full name, email, role, account status and `mustChangePassword`, never stored password/hash fields. Login responses include the new flag while retaining the current role names and structure.

The existing audit trail records `ADMIN_DISABLED_USER`, `ADMIN_ENABLED_USER`, `ADMIN_INITIATED_PASSWORD_RESET` and `REQUIRED_PASSWORD_CHANGED`. Administrative records contain the actor's ID/email/role and the affected user's ID/email; the audit entity supplies the timestamp. No passwords are included.

## Tests

```powershell
./mvnw.cmd '-Dtest=!BackendApplicationTests' test
```

`UserManagementFlowTests` exercises real Spring Security, JWTs, BCrypt, controllers and services with mocked persistence. It covers all account roles, disabling/re-enabling, self-protection, direct unauthorized API calls, temporary-password login, forced changes, BCrypt persistence, old-token invalidation, public-reset bypass prevention and password-free audit records. Existing authentication/evidence tests remain in the suite.

Frontend checks: `npm run build`, `npm run lint`, `npm run test:smoke`, `npm run test:layout`, and `scripts/user-management-test.mjs` (Playwright/Edge; `PLAYWRIGHT_MODULE` may point to the installed runtime, `VISUAL_BASE_URL` defaults to `http://127.0.0.1:5186`). Browser tests use a mocked API and do not change real accounts. They cover searching/filtering, confirmations, errors, all reset targets, one-time display, role guards, the mandatory-change flow for all roles, and light/dark mobile/desktop layouts.

## Existing deployment limitations

This is not an authentication replacement or production-hardening project. The existing hardcoded JWT signing key and development provider password-reset token delivery remain production risks. Secure those and use HTTPS before exposing the system publicly. Bootstrap credentials and user management continue to reuse the existing account architecture.
