-- Apply once after a backup. Existing single-file evidence stays valid.
ALTER TABLE phase_evidence ADD COLUMN submission_id VARCHAR(36) NULL;
