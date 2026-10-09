ALTER TABLE articles ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE TRIGGER trg_articles_updated_at BEFORE UPDATE ON articles FOR EACH ROW EXECUTE FUNCTION set_updated_at();
ALTER TABLE store_policies ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN activated_at TIMESTAMPTZ;
UPDATE store_policies SET activated_at = updated_at WHERE status = 'ACTIVE';
DROP INDEX uq_store_policies_active_type;
CREATE UNIQUE INDEX uq_store_policies_active_type ON store_policies(policy_type) WHERE status = 'ACTIVE' AND NOT deleted;
CREATE INDEX idx_articles_visible_type_created ON articles(article_type, status, created_at DESC, id) WHERE NOT deleted;
CREATE INDEX idx_policies_type_created ON store_policies(policy_type, created_at DESC, id) WHERE NOT deleted;
CREATE INDEX idx_payments_paid_at ON payments(paid_at) WHERE status = 'PAID';
CREATE INDEX idx_refunds_processed_at ON refunds(processed_at) WHERE status = 'SUCCEEDED';
CREATE INDEX idx_orders_placed_status ON orders(placed_at, order_status, id);

INSERT INTO permissions(name, code, module) VALUES
    ('Read articles and lookbooks', 'ARTICLE_READ', 'CONTENT'), ('Manage articles and lookbooks', 'ARTICLE_WRITE', 'CONTENT'),
    ('Read store policies', 'POLICY_READ', 'CONTENT'), ('Manage store policies', 'POLICY_WRITE', 'CONTENT'),
    ('Read revenue reports', 'REPORT_REVENUE_READ', 'REPORT'), ('Read order reports', 'REPORT_ORDER_READ', 'REPORT'),
    ('Read bestselling reports', 'REPORT_PRODUCT_READ', 'REPORT'), ('Read inventory reports', 'REPORT_INVENTORY_READ', 'REPORT'),
    ('Read customer reports', 'REPORT_CUSTOMER_READ', 'REPORT'), ('Read return reports', 'REPORT_RETURN_READ', 'REPORT'),
    ('Read promotion reports', 'REPORT_PROMOTION_READ', 'REPORT')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN' AND NOT r.deleted AND NOT p.deleted
  AND p.code IN ('ARTICLE_READ','ARTICLE_WRITE','POLICY_READ','POLICY_WRITE','REPORT_REVENUE_READ','REPORT_ORDER_READ',
    'REPORT_PRODUCT_READ','REPORT_INVENTORY_READ','REPORT_CUSTOMER_READ','REPORT_RETURN_READ','REPORT_PROMOTION_READ')
ON CONFLICT (role_id, permission_id) DO NOTHING;
