-- Add a nullable reference from orders to the selected EFT bank account.
-- This allows each EFT order to keep the exact account chosen at checkout.

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS eft_bank_details_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_orders_eft_bank_details'
          AND table_name = 'orders'
    ) THEN
        ALTER TABLE orders
        ADD CONSTRAINT fk_orders_eft_bank_details
        FOREIGN KEY (eft_bank_details_id)
        REFERENCES bank_details(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_orders_eft_bank_details_id
ON orders(eft_bank_details_id);

