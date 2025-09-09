-- Migration script to DROP and RECREATE promo_codes table
-- WARNING: This will delete ALL existing promo code data!
-- Run this SQL script on your database to completely recreate the table

-- Drop the existing table if it exists (this deletes all data!)
DROP TABLE IF EXISTS promo_codes CASCADE;

-- Create the table fresh with correct schema
CREATE TABLE promo_codes (
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

-- Create indexes for better performance
CREATE INDEX idx_promo_codes_code ON promo_codes(code);
CREATE INDEX idx_promo_codes_expiry_date ON promo_codes(expiry_date);

-- Add unique constraint on code (already specified in column definition, but explicit)
CREATE UNIQUE INDEX promocode_code_key ON promo_codes(code);

-- Add comments for documentation
COMMENT ON TABLE promo_codes IS 'Stores promo codes that can be applied during checkout';
COMMENT ON COLUMN promo_codes.code IS 'Unique promo code string (e.g., SAVE10)';
COMMENT ON COLUMN promo_codes.percentage_discount IS '1 for percentage discount, 0 for fixed amount discount';
COMMENT ON COLUMN promo_codes.discount_amount IS 'Either percentage (e.g., 10 for 10%) or fixed amount (e.g., 20.00 for R20)';
COMMENT ON COLUMN promo_codes.minimum_order_amount IS 'Minimum order amount required to apply this promo code';

-- Insert some sample data for testing (optional)
INSERT INTO promo_codes (code, discount_amount, percentage_discount, max_usages, usage_count, expiry_date, minimum_order_amount, description) VALUES
('WELCOME10', 10.00, 1, 100, 0, '2025-12-31', 0.0, '10% off for new customers'),
('SAVE20', 20.00, 0, 50, 0, '2025-12-31', 100.0, 'R20 off orders over R100'),
('BURGER15', 15.00, 1, 200, 0, '2025-12-31', 50.0, '15% off burgers - minimum R50');

-- Display the new table structure
\d promo_codes;

-- Show sample data
SELECT * FROM promo_codes;
