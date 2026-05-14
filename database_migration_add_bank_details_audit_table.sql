-- Add optimistic locking to bank_details and an append-only audit trail for admin edits.

ALTER TABLE bank_details
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

UPDATE bank_details
SET version = 0
WHERE version IS NULL;

ALTER TABLE bank_details
ALTER COLUMN version SET NOT NULL;

CREATE TABLE IF NOT EXISTS bank_details_audit (
    id BIGSERIAL PRIMARY KEY,
    bank_details_id BIGINT NULL,
    action VARCHAR(20) NOT NULL,
    actor_username VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    before_snapshot_json TEXT NULL,
    after_snapshot_json TEXT NULL,
    CONSTRAINT fk_bank_details_audit_bank_details
        FOREIGN KEY (bank_details_id)
        REFERENCES bank_details(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_bank_details_audit_changed_at
    ON bank_details_audit(changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_bank_details_audit_bank_details_id
    ON bank_details_audit(bank_details_id);

