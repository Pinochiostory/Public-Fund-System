-- Apply once to the existing application database, after a backup.
-- Nullable columns preserve existing projects. Never manufacture historic reviews.
ALTER TABLE projects
  ADD COLUMN closeout_approved_budget DECIMAL(15,2) NULL,
  ADD COLUMN closeout_released_amount DECIMAL(15,2) NULL,
  ADD COLUMN closeout_retained_amount DECIMAL(15,2) NULL,
  ADD COLUMN closeout_initiated_by BIGINT NULL,
  ADD COLUMN closeout_reviewed_by BIGINT NULL,
  ADD COLUMN closeout_reviewed_at DATETIME(6) NULL,
  ADD COLUMN closeout_reference VARCHAR(100) NULL,
  ADD COLUMN closeout_note VARCHAR(1000) NULL;
