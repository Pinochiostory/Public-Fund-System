# Multi-file evidence submissions

The existing evidence table and review endpoints are reused. Migration
`migrations/005_evidence_submissions.sql` adds one nullable `submission_id`
column; historic single-file records remain unchanged. Back up the database
before applying it to another installation. Restart the backend after updating.

- Providers select 1–10 files in one submission: each under 25 MiB, combined
  under 100 MiB. Supported content: PDF, PNG, JPEG and DOCX.
- `POST /api/evidence/phase/{phaseId}/batch` accepts repeated multipart `files`
  fields. The original single-file endpoint remains compatible.
- The backend validates ownership, active phase, actual content, limits and
  duplicate content before saving. One project lock and transaction protect
  the complete set. Storage failures roll back the rows and newly saved files.
- Only one submission can await review per phase. Each document is reviewed
  separately. The phase advances only when every file in the latest submission
  is approved; Administrator conclusion remains a separate existing action.
- If a file is rejected or withdrawn, finish reviewing the remaining files,
  then submit a complete corrected set. Earlier files and reviews stay in history.
- The frontend shows file names, types, sizes, removal controls, upload progress
  and a success dialog only after the server confirms the submission.

Verification: WorkflowSafetyIntegrationTests covers batch approval, rejection /
resubmission history, invalid and duplicate files, role / ownership denial,
storage rollback and concurrent duplicate submissions. The frontend
workflow-safety-test.mjs covers multi-file selection, failure / retry and the
success pop-up alongside existing workflow checks.
