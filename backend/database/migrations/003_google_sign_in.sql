-- Back up first. Additive migration; do not rerun blindly.
ALTER TABLE users ADD COLUMN google_subject VARCHAR(255) NULL,
  ADD CONSTRAINT uq_users_google_subject UNIQUE (google_subject);
