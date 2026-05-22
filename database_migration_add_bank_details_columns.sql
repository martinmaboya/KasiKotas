-- database_migration_add_bank_details_columns.sql
-- Adds checksum and archive columns to bank_details table and snapshot fields to orders if missing.
-- Run on PostgreSQL as DB owner.

-- 1) Add checksum columns to bank_details (nullable)
ALTER TABLE bank_details
    ADD COLUMN IF NOT EXISTS account_number_checksum varchar(64),
    ADD COLUMN IF NOT EXISTS account_name_checksum varchar(64),
    ADD COLUMN IF NOT EXISTS bank_name_checksum varchar(64),
    ADD COLUMN IF NOT EXISTS last_verified_at timestamp;

-- 2) Add is_archived column if missing (boolean)
ALTER TABLE bank_details
    ADD COLUMN IF NOT EXISTS is_archived boolean DEFAULT false;

-- 3) Ensure orders table has EFT snapshot columns (if your Order entity uses them)
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS eft_bank_details_id bigint,
    ADD COLUMN IF NOT EXISTS eft_bank_name varchar(255),
    ADD COLUMN IF NOT EXISTS eft_account_name varchar(255),
    ADD COLUMN IF NOT EXISTS eft_account_number varchar(255),
    ADD COLUMN IF NOT EXISTS eft_shap_id varchar(255),
    ADD COLUMN IF NOT EXISTS eft_branch_code varchar(255);

-- 4) If you need to populate existing orders that referenced bank_details via eft_bank_details_id
-- with the current snapshot from bank_details, run an update (careful: only for non-null refs)
UPDATE orders o
SET eft_bank_name = bd.bank_name,
    eft_account_name = bd.account_name,
    eft_account_number = bd.account_number,
    eft_shap_id = bd.shap_id,
    eft_branch_code = bd.branch_code
FROM bank_details bd
WHERE o.eft_bank_details_id = bd.id
  AND (o.eft_bank_name IS NULL OR o.eft_account_number IS NULL);

-- 5) Add index to bank_details.lookup (helpful when selecting non-archived)
CREATE INDEX IF NOT EXISTS idx_bank_details_is_archived ON bank_details (is_archived);

