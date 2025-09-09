-- Migration script to create or fix promo_codes table
-- Run this SQL script on your database to create/update the promo_codes table

-- Create the table if it doesn't exist
CREATE TABLE IF NOT EXISTS promo_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(255) NOT NULL UNIQUE,
    discount_amount DECIMAL(10,2) NOT NULL,
    percentage_discount INTEGER NOT NULL DEFAULT 0, -- 1 = percentage, 0 = fixed amount
    max_usages INTEGER NOT NULL DEFAULT 1,
    usage_count INTEGER NOT NULL DEFAULT 0,
    expiry_date DATE NOT NULL,
    minimum_order_amount DECIMAL(10,2) NOT NULL DEFAULT 0.0,
    description TEXT,
    version BIGINT DEFAULT 0
);

-- If the table already exists but has wrong column type, fix it
-- WARNING: This will convert existing boolean values to integers
-- true -> 1, false -> 0
DO $$
BEGIN
    -- Check if the column exists and is of wrong type
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'promo_codes' 
        AND column_name = 'percentage_discount' 
        AND data_type != 'integer'
    ) THEN
        -- Drop the constraint temporarily if it exists
        ALTER TABLE promo_codes ALTER COLUMN percentage_discount DROP NOT NULL;
        
        -- Convert boolean to integer (PostgreSQL specific)
        ALTER TABLE promo_codes ALTER COLUMN percentage_discount TYPE INTEGER 
        USING (CASE WHEN percentage_discount::boolean THEN 1 ELSE 0 END);
        
        -- Re-add the not null constraint
        ALTER TABLE promo_codes ALTER COLUMN percentage_discount SET NOT NULL;
        
        -- Set default value
        ALTER TABLE promo_codes ALTER COLUMN percentage_discount SET DEFAULT 0;
        
        RAISE NOTICE 'Successfully converted percentage_discount column from boolean to integer';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Could not convert percentage_discount column: %', SQLERRM;
END $$;

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_promo_codes_code ON promo_codes(code);
CREATE INDEX IF NOT EXISTS idx_promo_codes_expiry_date ON promo_codes(expiry_date);

-- Add comments for documentation
COMMENT ON TABLE promo_codes IS 'Stores promo codes that can be applied during checkout';
COMMENT ON COLUMN promo_codes.code IS 'Unique promo code string (e.g., SAVE10)';
COMMENT ON COLUMN promo_codes.percentage_discount IS '1 for percentage discount, 0 for fixed amount discount';
COMMENT ON COLUMN promo_codes.discount_amount IS 'Either percentage (e.g., 10 for 10%) or fixed amount (e.g., 20.00 for R20)';
COMMENT ON COLUMN promo_codes.minimum_order_amount IS 'Minimum order amount required to apply this promo code';
