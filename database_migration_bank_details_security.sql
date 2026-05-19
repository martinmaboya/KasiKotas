-- Database migration to add security enhancements to bank_details table
-- This migration adds checksums for tamper detection and archival flag for soft-delete
-- Date: May 19, 2026

-- Add checksum columns for tamper detection
ALTER TABLE bank_details
ADD COLUMN IF NOT EXISTS account_number_checksum VARCHAR(64),
ADD COLUMN IF NOT EXISTS account_name_checksum VARCHAR(64),
ADD COLUMN IF NOT EXISTS bank_name_checksum VARCHAR(64);

-- Add last verified timestamp
ALTER TABLE bank_details
ADD COLUMN IF NOT EXISTS last_verified_at TIMESTAMP;

-- Add soft-delete flag (archival)
ALTER TABLE bank_details
ADD COLUMN IF NOT EXISTS is_archived BOOLEAN DEFAULT false;

-- Create index on is_archived for faster filtering of active records
CREATE INDEX IF NOT EXISTS idx_bank_details_active
ON bank_details(is_archived) WHERE is_archived = false;

-- Add trigger to update last_verified_at on INSERT/UPDATE
CREATE OR REPLACE FUNCTION update_bank_details_last_verified()
RETURNS TRIGGER AS $$
BEGIN
  NEW.last_verified_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS bank_details_update_timestamp ON bank_details;
CREATE TRIGGER bank_details_update_timestamp
BEFORE INSERT OR UPDATE ON bank_details
FOR EACH ROW
EXECUTE FUNCTION update_bank_details_last_verified();

-- Add audit logging trigger to log all changes to bank_details_audit
CREATE OR REPLACE FUNCTION log_bank_details_change()
RETURNS TRIGGER AS $$
BEGIN
  IF (TG_OP = 'DELETE') THEN
    INSERT INTO bank_details_audit (bank_details_id, action, actor_username, changed_at, before_snapshot_json, after_snapshot_json)
    VALUES (OLD.id, 'DELETE', CURRENT_USER, CURRENT_TIMESTAMP, row_to_json(OLD), NULL);
    RETURN OLD;
  ELSIF (TG_OP = 'UPDATE') THEN
    INSERT INTO bank_details_audit (bank_details_id, action, actor_username, changed_at, before_snapshot_json, after_snapshot_json)
    VALUES (NEW.id, 'UPDATE', CURRENT_USER, CURRENT_TIMESTAMP, row_to_json(OLD), row_to_json(NEW));
    RETURN NEW;
  ELSIF (TG_OP = 'INSERT') THEN
    INSERT INTO bank_details_audit (bank_details_id, action, actor_username, changed_at, before_snapshot_json, after_snapshot_json)
    VALUES (NEW.id, 'CREATE', CURRENT_USER, CURRENT_TIMESTAMP, NULL, row_to_json(NEW));
    RETURN NEW;
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS bank_details_audit_trigger ON bank_details;
CREATE TRIGGER bank_details_audit_trigger
AFTER INSERT OR UPDATE OR DELETE ON bank_details
FOR EACH ROW
EXECUTE FUNCTION log_bank_details_change();

-- Add constraint to ensure active bank details are not duplicated by account number
-- (Only one active account with the same account number)
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_account_number
ON bank_details(account_number) WHERE is_archived = false;

-- Add audit table constraints if not exists
ALTER TABLE bank_details_audit
ADD CONSTRAINT fk_bank_details_audit_id
FOREIGN KEY (bank_details_id) REFERENCES bank_details(id) ON DELETE CASCADE;

-- Grant select permission to application user
-- (Adjust 'kasikotas_db_si2n_user' to match your actual app user)
GRANT SELECT, INSERT, UPDATE, DELETE ON bank_details TO kasikotas_db_si2n_user;
GRANT SELECT, INSERT ON bank_details_audit TO kasikotas_db_si2n_user;
GRANT USAGE, SELECT ON SEQUENCE bank_details_id_seq TO kasikotas_db_si2n_user;
GRANT USAGE, SELECT ON SEQUENCE bank_details_audit_id_seq TO kasikotas_db_si2n_user;

-- Verify migration completed
SELECT 'Bank details security migration completed successfully' as status;
SELECT COUNT(*) as bank_details_count FROM bank_details;
SELECT COUNT(*) as audit_records_count FROM bank_details_audit;

