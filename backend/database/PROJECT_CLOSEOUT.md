# Project closeout and phase order

The existing project, phase, disbursement, notification and audit systems remain authoritative. No bank-transfer system or second ledger was added.

## Undisbursed allocation

At formal project completion, the project row is locked. Every non-rejected phase must be completed and there must be no pending/processing payments. Released payments must not exceed the approved budget.

The approved budget, released amount, undisbursed remainder and completing Administrator's user ID are recorded on the existing project. Completed projects cannot accept further phase funding, payment processing or releases through the existing APIs. The remainder stays frozen with the funder. It is not written off, automatically distributed, or made available to an Administrator.

A different active Administrator records an independent retention review using a funder reconciliation reference and explanation. The server verifies identity, role, record version, unchanged financial totals, and that no review has already been saved. Review does not authorize payment or change the balance. Both completion and review use the existing audit trail; providers can see the outcome.

Old completed projects without a snapshot are explicitly shown as needing reconciliation. Historic decisions are not invented or backfilled.

## Important boundary

Approved budget minus released payments measures **undisbursed allocation**, not unspent cash held by a provider. Money already paid must be reconciled against actual spending and, if necessary, returned to an independently verified funder account through the organization's banking/accounting process. This feature does not certify a bank balance, execute a return, or prevent misuse of bank access outside the application. A reference and two-person review are accountability controls, not bank-statement verification.

## Database

Migration: `migrations/004_project_closeout.sql`, eight nullable columns on `projects`. Back up first and apply once when managing schema manually. Existing records and users are preserved. The local development database was backed up before applying the migration; backups contain sensitive records and must remain excluded from Git.

## Phase sequence

The provider UI offers Start only for a funded phase in an active project, with all earlier non-rejected phases completed and no other phase in progress or awaiting formal completion. The backend independently checks the same rules under the existing project lock. Evidence approval alone is not completion; the existing formal phase-conclusion step remains required.

## Validation

The opt-in `WorkflowSafetyIntegrationTests` suite uses only `public_fund_workflow_test` (create/drop). It includes concurrent starts, legacy out-of-order phases, independent closeout review, stale requests, repeated reviews, unauthorized roles, overspending, unsettled payments and blocked post-closeout releases.
