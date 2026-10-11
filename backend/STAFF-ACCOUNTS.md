# Database-backed staff accounts

The existing `users` table, `User` entity, `UserRepository`, BCrypt bean and JWT login are reused. No schema migration is required. No real accounts or passwords were added as part of the code change.

## First administrator

In the same PowerShell terminal that will start the backend:

```powershell
cd C:\Users\Admin\Desktop\codex_project\Public-Fund-System\backend
$env:PUBLIC_FUND_ADMIN_NAME = Read-Host 'Administrator full name'
$env:PUBLIC_FUND_ADMIN_EMAIL = Read-Host 'Administrator email'
$bootstrapPassword = Read-Host 'Administrator password' -AsSecureString
$env:PUBLIC_FUND_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $bootstrapPassword).Password
try {
    .\mvnw.cmd spring-boot:run
} finally {
    Remove-Item Env:PUBLIC_FUND_ADMIN_NAME, Env:PUBLIC_FUND_ADMIN_EMAIL, Env:PUBLIC_FUND_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable bootstrapPassword -ErrorAction SilentlyContinue
}
```

Start MySQL first. If using VS Code's Java Run button, configure these variables in that process's environment instead; variables in a terminal are inherited only by processes launched from that terminal. Do not commit real credentials to source, `application.properties`, or a shared launch configuration. Spring Boot does not automatically read a `.env` file.

The password must satisfy the existing rules: at least 8 characters, with uppercase, lowercase, a number and a special character. Use a unique strong password. BCrypt is used through the existing `PasswordEncoder`.

- All three variables absent: seeding is skipped, with no default account.
- Partial configuration: startup fails with a configuration message.
- Email absent from `users`: one `ADMIN` is created with the configured full name and a BCrypt hash.
- Email already belongs to an admin: nothing is changed, including password, name and lockout state.
- Email belongs to a non-admin: startup fails rather than promoting or overwriting that account.

After the first successful seed, remove the bootstrap environment variables from your launch configuration. Normal subsequent starts need no bootstrap variables. Changing a seed password does **not** reset an existing account's password.

## Create officers and additional admins

1. Sign in through the existing login page using the seeded administrator.
2. Open **Staff accounts** in the Admin sidebar.
3. Provide the person's full name, email, password and role.
4. Share credentials securely. Account creation does not send email.

Officers and additional Admins use the same login page. Existing sessions for the former hardcoded accounts must sign in again; synthetic user IDs `0` and `-1` no longer authenticate.

The new endpoint is `POST /api/auth/staff`, restricted by Spring Security to an authenticated, active administrator. It accepts form fields `fullName`, `email`, `password`, and `role` (`ADMIN` or `GOVERNMENT_OFFICER`) and returns a password-free account DTO with HTTP 201. It cannot create providers or arbitrary roles, overwrite an existing email, or return password hashes. Public `/api/auth/register` continues to accept only `SERVICE_PROVIDER`.

## Role compatibility

New staff rows use `ADMIN` and `GOVERNMENT_OFFICER` as requested. Existing frontend/API login and security role names stay `ADMINISTRATOR` and `VERIFICATION_OFFICER`, mapped at the authentication boundary. Existing database rows with those older role labels still work. No existing users are deleted, promoted, or rewritten. `/api/auth/admin-login` remains available as a database-backed compatibility endpoint, while the frontend uses `/api/auth/login` once for all roles. Audit entries now record actual database user IDs for admins.

## Existing security limitations (not a production deployment)

Public provider password recovery now uses an email-only, single-use link; the API does not return reset tokens. Staff recovery is available to another Admin through **User Management**, using a one-time displayed temporary password and a mandatory password change. Public provider recovery cannot bypass a disabled account or a required password change. Bootstrap does not unlock or reset existing accounts. See `USER-MANAGEMENT.md` for the workflow.

The JWT signing key is supplied using `PUBLIC_FUND_JWT_SECRET` through the environment or ignored `config/local.properties`, not Java source. It must contain at least 32 bytes; use a strong random value. Use a separate new secret for production. Changing this key invalidates existing sessions. Database-backed accounts and BCrypt alone do not make a development deployment production-safe.

## Verification

Focused tests do not write to MySQL:

```powershell
./mvnw.cmd '-Dtest=ProvisionedStaffTests,InitialAdminSeederTests,DatabaseStaffTokenTests,StaffAccountSecurityTests,StaffPasswordResetTests,EvidenceFileAccessTests,EvidenceUploadValidationTests' test
```

These cover role restrictions, admin-only access, BCrypt, bootstrap idempotence, email collisions, login compatibility, lockouts, invalidated synthetic staff tokens, password-safe responses and provider password-reset compatibility.

Verified locally: 45 focused backend tests passed, plus the full Spring application context test against MySQL with `ddl-auto=validate` and bootstrap explicitly disabled. The frontend production build, route smoke checks, layout checks and mocked browser tests for staff creation passed. Existing frontend lint warnings remain; no new lint errors were introduced.
