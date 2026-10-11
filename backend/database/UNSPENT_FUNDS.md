# Expenditure and unspent-fund reconciliation

This extends the existing project completion, file storage, audit, notification
and role systems. It does not make bank transfers or integrate with a bank.

## Balances

- Undisbursed allocation = approved project budget minus gross recorded payments.
- Unspent advance = gross recorded payments minus approved actual expenditure.
- Outstanding return = unspent advance minus independently verified returns.

Returns never reduce original payment records or increase the project's available
payout balance. A provider declaration is not approved expenditure; an uploaded
return proof is not verified receipt. The UI keeps these states separate.

## Workflow

1. Finish phases and use the existing Request completion action.
2. In Projects, the provider declares expenditure and attaches a statement with
   supporting receipts (PDF/PNG/JPG, under 25 MiB). A combined PDF may contain
   several receipts; phase evidence remains separately available.
3. An Administrator reviews the statement, giving a reason for approval/rejection.
   Rejection permits a new submission; original records/files remain available.
4. If an unspent amount remains, the provider uses the official funder instructions
   outside the platform, then records the amount, date, reference and proof of
   the completed transfer. Partial returns are supported, one pending at a time.
5. An Administrator other than the expenditure reviewer checks receipt in the
   funder's bank/ledger records and enters the unique funder transaction reference.
   Proof supplied by the provider alone is insufficient. Rejected proofs remain
   in history and can be corrected through a new submission.
6. Only when expenditure is approved and the outstanding return is zero can the
   Administrator complete the project. Existing undisbursed-allocation closeout
   and independent review remain unchanged.

Historical COMPLETED projects are not rewritten or automatically marked financially
reconciled. Their new reconciliation section starts as not submitted. Gross payments,
old completion dates and closeout snapshots remain intact.

## Configure the official destination

The system owner must independently verify the funder account and configure
`PUBLIC_FUND_RETURN_DESTINATION` in the backend environment before startup. Include
the official account holder, bank, account number, branch code and reference
instructions. Never use personal Admin banking details, passwords or login secrets.
The value can contain line breaks, up to 1,500 characters. No default account exists.

When unset, expenditure review works, but the return form/API refuses submissions
and tells the provider to contact the owner. Administrators cannot change this
configuration through the UI or API. Each submitted return preserves the destination
as displayed; a fingerprint rejects submissions from stale instruction screens.
Existing claims against older destination snapshots need careful independent review.

This is an application-level safeguard, not protection against someone controlling
the server, database or bank account. Production banking should have its own
independent authorization and reconciliation controls. Do not assume this platform
has moved, frozen or recovered actual bank funds.

## Database rollout

Back up first. Use exactly one migration path:

- New table: `migrations/006_project_reconciliations.sql`.
- If Hibernate already created the table: inspect its columns/constraints, then
  apply `migrations/006b_existing_reconciliation_constraints.sql` only if those
  constraints are missing. Do not run both scripts on the same installation.

One new table stores expenditure declarations and return claims because neither
amount was represented previously. It is not a second users/payments/evidence table.
Files use the existing secure storage service in its reconciliation subdirectory.
Preserve uploaded files alongside database backups; a DB backup alone cannot restore
documents. Never remove records to resolve a discrepancy.

## Safety and API

`GET /api/projects/{projectId}/reconciliation` returns balances and submission history.
Provider-only multipart POSTs: `/declarations` and `/returns` under that path.
Admin-only POST `/{id}/review`; authorized GET `/{id}/file` downloads supporting files.
Project ownership is enforced server-side. Officers cannot access these endpoints.

Mutations require `If-Match`: project version for declarations, approved declaration
version for return submissions, submission version for reviews. Project-row locks
serialize operations. A unique receipt key prevents counting one bank receipt twice,
including across different projects. Duplicate or excess returns are rejected.
All submissions/reviews create entries in the existing project audit timeline.
No edits/deletes of reviewed submissions are exposed. Approved expenditure mistakes,
overpayments, or changed payment totals require investigation; this feature does not
silently adjust them or offer an unaudited override.

Tests: WorkflowSafetyIntegrationTests (opt-in isolated MySQL test database) and
frontend/scripts/reconciliation-test.mjs (browser workflow with mocked API).
