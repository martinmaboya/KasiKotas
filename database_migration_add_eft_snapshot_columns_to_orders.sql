-- Add immutable EFT snapshot columns to orders so payment instructions do not depend on the live bank_details row.
-- Existing EFT orders are backfilled from their currently linked bank_details record.

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS eft_bank_name VARCHAR(255),
ADD COLUMN IF NOT EXISTS eft_account_name VARCHAR(255),
ADD COLUMN IF NOT EXISTS eft_account_number VARCHAR(255),
ADD COLUMN IF NOT EXISTS eft_shap_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS eft_branch_code VARCHAR(255);

UPDATE orders o
SET eft_bank_name = COALESCE(o.eft_bank_name, bd.bank_name),
    eft_account_name = COALESCE(o.eft_account_name, bd.account_name),
    eft_account_number = COALESCE(o.eft_account_number, bd.account_number),
    eft_shap_id = COALESCE(o.eft_shap_id, bd.shap_id),
    eft_branch_code = COALESCE(o.eft_branch_code, bd.branch_code)
FROM bank_details bd
WHERE o.eft_bank_details_id = bd.id
  AND LOWER(COALESCE(o.payment_method, '')) = 'eft';

