# Dashboard redesign

The frontend now uses a shared navigation shell, flat clickable overview metrics,
search/status controls, reduced-motion-aware loading skeletons, and a public
`/how-it-works` guide. The previous layered `design-system.css` is no longer imported.
Project approvals, phase transitions, evidence reviews and disbursements retain
their existing backend business logic.

## Development staff accounts

The existing administrator credentials are unchanged.

Staff provisioning now includes a display name. The third field of each
AdminConfig.ADMINISTRATORS entry is the administrator's full name; the officer
name is OfficerConfig.FULL_NAME. Alex Morgan and Jamie Parker are demo names
to replace when provisioning the actual accounts. Sign in again after a backend
restart to refresh an existing session's display name. Emails, password hashes
and permissions are unchanged by this display-name update.

Provider payments are grouped at /provider/disbursements, with project details
at /provider/disbursements/:projectId. Evidence history uses
/provider/evidence/:projectId/phases/:phaseId. File downloads enforce uploader
ownership, and uploads enforce project ownership and a file size below 25 MB.
Run node frontend/scripts/project-flows-test.mjs from the project root for
payment-isolation and upload-size checks.

The configured verification officer signs in through the normal login screen:

- Email: `officer@publicfund.gov.za`
- Development password: `Officer@123`

`backend/src/main/java/com/publicfund/backend/config/OfficerConfig.java` stores
the email and BCrypt password hash. Change the hash before deployment; this
documented demonstration password is not a production secret.
Restart the backend after changing code-provisioned credentials.
Officers have verification permissions, not administrator permissions.
Public registration accepts service providers only and reserves staff emails.
Existing database officer records are not deleted, but those accounts can no
longer sign in and JWTs for unconfigured officer emails are no longer accepted.
Provisioned staff passwords are maintained in configuration, not through the
database-backed password-reset screen.

## Checks

From `frontend`: `npm run build`, `npm run lint`, `npm run test:smoke`.
The smoke test renders routes and common components; it is not a browser visual
or end-to-end API test.

From `backend`: `.\mvnw.cmd -Dtest=ProvisionedStaffTests test`.
The focused authentication tests do not require a running database.
