-- Adds inventory tracking for extras and required-extra mapping per product.

ALTER TABLE extras
    ADD COLUMN IF NOT EXISTS stock INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS product_extra_requirements (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    extra_id BIGINT NOT NULL,
    units_required INTEGER NOT NULL,
    CONSTRAINT fk_per_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_per_extra FOREIGN KEY (extra_id) REFERENCES extras(id) ON DELETE CASCADE,
    CONSTRAINT uk_per_product_extra UNIQUE (product_id, extra_id)
);

CREATE INDEX IF NOT EXISTS idx_per_product_id ON product_extra_requirements(product_id);
CREATE INDEX IF NOT EXISTS idx_per_extra_id ON product_extra_requirements(extra_id);


