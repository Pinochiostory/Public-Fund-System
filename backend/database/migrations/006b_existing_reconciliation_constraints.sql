-- Alternative for installations where Hibernate already created the empty table.
-- Do not also run this after 006_project_reconciliations.sql.
-- Inspect constraints and back up first; preserves all rows.
ALTER TABLE project_reconciliations
ADD CONSTRAINT fk_reconciliation_project FOREIGN KEY (project_id) REFERENCES projects(id),
ADD CONSTRAINT fk_reconciliation_declaration FOREIGN KEY (declaration_id) REFERENCES project_reconciliations(id),
ADD CONSTRAINT fk_reconciliation_provider FOREIGN KEY (submitted_by) REFERENCES users(id),
ADD CONSTRAINT fk_reconciliation_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
ADD CONSTRAINT ck_reconciliation_kind CHECK (kind IN ('DECLARATION','RETURN')),
ADD CONSTRAINT ck_reconciliation_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
ADD CONSTRAINT ck_reconciliation_amount CHECK (amount >= 0 AND released_amount >= amount AND (kind = 'DECLARATION' OR amount > 0));
