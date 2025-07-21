-- Migration script to add scheduled_delivery_time column to orders table
-- Run this SQL script on your database to add the new column

ALTER TABLE orders ADD COLUMN scheduled_delivery_time TIMESTAMP NULL;

-- Optional: Add an index on the scheduled_delivery_time column for better query performance
CREATE INDEX idx_orders_scheduled_delivery_time ON orders(scheduled_delivery_time);

-- Optional: Add a comment to the column for documentation
COMMENT ON COLUMN orders.scheduled_delivery_time IS 'The scheduled delivery time for the order. NULL for immediate delivery.';
