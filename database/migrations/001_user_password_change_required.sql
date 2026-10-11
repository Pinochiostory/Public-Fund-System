-- Apply once to existing deployments that do not use Hibernate ddl-auto=update.
-- Existing accounts retain their current passwords, status and access.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
