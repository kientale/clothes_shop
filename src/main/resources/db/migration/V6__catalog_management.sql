-- Catalog management: soft deletion and timestamps for catalog tables, plus PRODUCT permissions.
-- Unique slugs/codes/SKUs keep covering soft-deleted rows, like role codes, so an old link never
-- silently points at a different item.
ALTER TABLE categories
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE brands
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE colors
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sizes
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE collections
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE products ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE product_variants ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TRIGGER trg_categories_updated_at BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_brands_updated_at BEFORE UPDATE ON brands
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_colors_updated_at BEFORE UPDATE ON colors
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_sizes_updated_at BEFORE UPDATE ON sizes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_collections_updated_at BEFORE UPDATE ON collections
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_categories_visible_created ON categories(created_at DESC, id) WHERE deleted = FALSE;
CREATE INDEX idx_brands_visible_created ON brands(created_at DESC, id) WHERE deleted = FALSE;
CREATE INDEX idx_collections_visible_created ON collections(created_at DESC, id) WHERE deleted = FALSE;
CREATE INDEX idx_products_visible_created ON products(created_at DESC, id) WHERE deleted = FALSE;
CREATE INDEX idx_product_variants_product ON product_variants(product_id) WHERE deleted = FALSE;

INSERT INTO permissions(name, code, module) VALUES
    ('Read catalog', 'PRODUCT_READ', 'PRODUCT'),
    ('Manage catalog', 'PRODUCT_WRITE', 'PRODUCT')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND r.deleted = FALSE AND p.deleted = FALSE
  AND p.code IN ('PRODUCT_READ', 'PRODUCT_WRITE')
ON CONFLICT (role_id, permission_id) DO NOTHING;
