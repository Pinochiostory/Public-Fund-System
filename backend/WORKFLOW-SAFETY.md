# Workflow safety and transparency

This is an extension of the existing application, not a replacement. Existing user roles, login/JWT/BCrypt, funding applications, projects, phases, evidence, payments, notifications and audit tables are reused.

## Workflow preserved

Application approval → project → phase proposal → phase approval → payment recorded → provider starts work → evidence review → Administrator concludes phase. Later phases require the previous phase to be completed. Evidence is reviewed by the existing Government Officer role or an Administrator.

Phase funding is an advance in this application; requiring evidence before the initial payment would deadlock this existing workflow. Payment recording does **not** perform or verify a bank transfer. The Administrator confirms the amount, project, phase and bank reference.

## Email setup

Set these environment variables on the backend process, then restart it:

- PUBLIC_FUND_MAIL_HOST
- PUBLIC_FUND_MAIL_PORT (default 587)
- PUBLIC_FUND_MAIL_USERNAME
- PUBLIC_FUND_MAIL_PASSWORD
- PUBLIC_FUND_MAIL_FROM
- PUBLIC_FUND_MAIL_TLS (default true; keep enabled for real providers)

Do not commit mail credentials or paste them into chat. A real email provider and a permitted sender address are required. The implementation is tested with a mail mock; external inbox delivery still needs an end-to-end test using the configured provider.

New Service Provider registrations start unverified. The registration response reports whether mail was sent. If delivery fails, the account remains unverified and the user can use Resend Code after delivery is configured. Existing users were preserved as verified to avoid unexpectedly blocking working accounts. Staff use their existing Admin-created accounts.

Verification codes last 10 minutes, are BCrypt-hashed, single-use, allow five guesses, and permit three sends/hour with a 60-second resend delay. Public registration/verification also has an in-process per-IP limiter. In a multi-instance deployment, put a shared rate limiter at the gateway; per-account limits are already persisted in MySQL. In-app notifications are authoritative. Optional notification email is sent after commit; delivery failures are audited without reversing the saved decision.

Password recovery now sends an email-only, single-use link valid for 30 minutes. The API never returns a reset credential; only its SHA-256 digest is saved in the existing reset-token table. Old plaintext development tokens are not accepted. Mail configuration/delivery failures are reported, not shown as success. Public recovery remains Service Provider-only; staff use the existing Admin-assisted recovery. See GOOGLE-AND-EMAIL-SETUP.md for local configuration and the optional Google sign-in extension.

## Data migration

Migration: database/migrations/002_workflow_transparency.sql.

Applied locally after a backup at database/backups/before-workflow-20261006.sql. The backup is ignored by Git and contains sensitive database data; keep it private. All 9 existing users were preserved. Do not rerun this migration blindly: MySQL DDL commits automatically.

The migration adds verification state, rejection metadata, optional deadlines, version columns, proof metadata, audit links, and a unique payment-per-phase constraint. No existing business tables were replaced. Historic missing dates/reasons/references/sizes are shown as not recorded, not fabricated. Historic audit rows remain available in the Admin audit trail; new linked events appear in each project's timeline.

## Financial and history safeguards

- Project-row locks serialize phase, evidence and payment changes; all related database changes and their audits are transactional.
- The payment-per-phase database constraint complements server duplicate checks.
- Versions plus If-Match detect stale decisions. State checks and row locks still protect callers that omit If-Match.
- Released funds are summed from RELEASED payments. Remaining project funds = approved budget − released funds. Unallocated phase budget is displayed separately.
- Insufficient funds reject the action and notify Administrators; budgets are never increased automatically.
- Completed-project remainders remain recorded, are displayed and trigger an Admin review notification.
- Approved financial values have no edit endpoint. They cannot be silently overwritten. Approval/payment amounts are recorded in audit events. No financial-adjustment feature or duplicate history table was introduced.
- Rejected evidence is retained. Pending evidence can be withdrawn for replacement with a reason, then a new submission is created.
- Payment proof is attached to the existing disbursement and retained. An existing proof cannot be silently overwritten.
- Phase start/completion timestamps cannot be overwritten through repeated workflow actions. Duration includes the review/formal-conclusion period; it is not a time-sheet measurement.

## Uploads

Evidence retains PDF, PNG, JPEG and DOCX support. Payment proof accepts PDF, PNG and JPEG. Files must be strictly less than 25 MB. Server validation checks signatures, image dimensions and DOCX archive structure/expansion limits. Files use generated storage names, downloads enforce ownership/role checks, storage paths are not returned in API data, and new files are removed on database rollback.

This is type/size validation, not malware scanning. Treat downloaded documents as untrusted. A production deployment should add scanning and a durable private storage/backup policy. Default storage is backend/uploads; app.upload-root can override it.

## Tests and operation

Local verification on 6 October 2026: 81 backend tests passed across the regression, file-safety, MySQL workflow and schema-validation runs. The frontend production build, 26-route smoke test, layout/project-flow checks, workflow browser tests and user-management browser tests passed. Browser suites use mocked APIs; the workflow integration suite separately exercises real MySQL and Spring Security. Real inbox delivery has not been tested.

Both development services were started and checked: frontend http://127.0.0.1:5173 and backend http://127.0.0.1:8080. Anonymous access to the Admin users endpoint returns 401, and the frontend origin passes CORS preflight. The backend was started with schema validation and initial-account seeding disabled for this verification.

Existing frontend/backend regression suites remain in place. New tests:

- WorkflowSafetyIntegrationTests: real Spring Security, APIs, BCrypt and MySQL, including competing payments, duplicate submissions, cross-provider access, OTP mistakes/expiry, evidence correction, payment proof and notification delivery failure.
- FileStorageSafetyTests: disguised/invalid files, size limits, storage failures, rollback cleanup and path restrictions.
- frontend/scripts/workflow-safety-test.mjs: browser interactions and responsive layouts with mock API data.

The MySQL suite is opt-in via PUBLIC_FUND_MYSQL_SAFETY_TEST=true and uses only the explicitly named public_fund_workflow_test database. It creates/drops its test tables. Never point it at production data. The normal application database is not cleared by these tests.

Run the backend from backend with .\\mvnw.cmd spring-boot:run, with MySQL running. Run the frontend from frontend with npm run dev. Restart an already-running backend after pulling these changes.
