-- Back up the database first. MySQL DDL auto-commits.
-- Preflight MUST return no rows; resolve existing duplicate payments manually, never delete history.
SELECT phase_id, COUNT(*) FROM disbursements GROUP BY phase_id HAVING COUNT(*) > 1;

-- Existing users retain access. New public registrations explicitly start unverified.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE email_verifications (
    user_id BIGINT NOT NULL PRIMARY KEY,
    code_hash VARCHAR(100) NULL,
    expires_at DATETIME(6) NULL,
    last_sent_at DATETIME(6) NULL,
    window_started_at DATETIME(6) NULL,
    send_count INT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
ALTER TABLE funding_applications
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN rejection_reason TEXT NULL,
    ADD COLUMN rejected_at DATETIME(6) NULL,
    ADD COLUMN rejected_by VARCHAR(255) NULL,
    ADD COLUMN expected_completion_date DATE NULL;
ALTER TABLE projects
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN expected_completion_date DATE NULL;
ALTER TABLE project_phases
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN deadline DATE NULL,
    ADD COLUMN rejected_at DATETIME(6) NULL,
    ADD COLUMN reviewed_by VARCHAR(255) NULL;
ALTER TABLE phase_evidence
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN file_size BIGINT NULL,
    ADD COLUMN reviewed_by VARCHAR(255) NULL;
ALTER TABLE disbursements
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN payment_reference VARCHAR(255) NULL,
    ADD COLUMN recorded_by VARCHAR(255) NULL,
    ADD COLUMN proof_name VARCHAR(255) NULL,
    ADD COLUMN proof_type VARCHAR(255) NULL,
    ADD COLUMN proof_size BIGINT NULL,
    ADD COLUMN proof_path VARCHAR(255) NULL,
    ADD COLUMN proof_uploaded_at DATETIME(6) NULL,
    ADD COLUMN proof_uploaded_by VARCHAR(255) NULL,
    ADD CONSTRAINT uk_disbursement_phase UNIQUE (phase_id);
ALTER TABLE audit_logs
    ADD COLUMN project_id BIGINT NULL,
    ADD COLUMN application_id BIGINT NULL,
    ADD INDEX ix_audit_project (project_id),
    ADD INDEX ix_audit_application (application_id);
-- Historic dates, reasons, references and file sizes remain NULL rather than being invented.
