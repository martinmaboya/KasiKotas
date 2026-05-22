-- database_migration_add_product_indexes.sql
-- Run these on your PostgreSQL database (psql) as the DB owner or a superuser.
-- 1) Enable useful extensions
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2) Basic indexes for product listing
CREATE INDEX IF NOT EXISTS idx_products_is_active_created_at ON products (is_active, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_products_category_is_active ON products (category_id, is_active);
CREATE INDEX IF NOT EXISTS idx_products_price ON products (price);
CREATE INDEX IF NOT EXISTS idx_products_name_lower ON products (lower(name));

-- 3) Trigram indexes for partial/fuzzy search
CREATE INDEX IF NOT EXISTS idx_products_name_trgm ON products USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_description_trgm ON products USING gin (coalesce(description, '') gin_trgm_ops);

-- 4) Optional: index on stock and archived flag if present
-- Uncomment and adjust column names if your schema uses them
-- CREATE INDEX IF NOT EXISTS idx_products_stock ON products (stock);
-- CREATE INDEX IF NOT EXISTS idx_products_is_archived ON products (is_archived);

-- 5) Materialized view example for top-sellers
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_top_products AS
SELECT p.id, p.name, p.price, p.main_image_path, SUM(oi.quantity) AS total_sold
FROM products p
JOIN order_items oi ON oi.product_id = p.id
WHERE p.is_active = true
GROUP BY p.id, p.name, p.price, p.main_image_path
ORDER BY total_sold DESC;

CREATE UNIQUE INDEX IF NOT EXISTS mv_top_products_pkey ON mv_top_products (id);
CREATE INDEX IF NOT EXISTS mv_top_products_total_sold ON mv_top_products (total_sold DESC);

-- Note: Refresh mat view with CONCURRENTLY when possible:
-- REFRESH MATERIALIZED VIEW CONCURRENTLY mv_top_products;
