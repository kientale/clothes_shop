-- Keep historical purchases, review evidence and campaign usage when an admin removes a record.
ALTER TABLE product_reviews ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN moderation_note TEXT, ADD COLUMN moderated_by UUID REFERENCES accounts(id),
    ADD COLUMN moderated_at TIMESTAMPTZ;

DO $$
DECLARE campaign_table TEXT;
BEGIN
    FOREACH campaign_table IN ARRAY ARRAY['coupons', 'promotions', 'flash_sales', 'banners', 'notifications'] LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP', campaign_table);
        IF campaign_table <> 'notifications' THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP', campaign_table);
        END IF;
        EXECUTE format('CREATE TRIGGER trg_%I_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()', campaign_table, campaign_table);
    END LOOP;
END $$;

ALTER TABLE notifications ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN published_at TIMESTAMPTZ;
-- Existing delivered notifications must remain visible after the migration.
UPDATE notifications n SET status = 'PUBLISHED', published_at = created_at
WHERE EXISTS (SELECT 1 FROM customer_notifications cn WHERE cn.notification_id = n.id);
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_status CHECK (status IN ('DRAFT', 'PUBLISHED'));
ALTER TABLE customer_notifications ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE TABLE notification_targets (
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id), PRIMARY KEY(notification_id, customer_id)
);
ALTER TABLE coupon_usages ADD COLUMN released BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN released_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_coupon_usages_release CHECK (released = (released_at IS NOT NULL));
CREATE TABLE order_marketing_lines (
    order_item_id UUID PRIMARY KEY REFERENCES order_items(id),
    flash_sale_item_id UUID REFERENCES flash_sale_items(id),
    promotion_id UUID REFERENCES promotions(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    discount_amount NUMERIC(18,2) NOT NULL CHECK (discount_amount >= 0),
    released BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_order_marketing_source CHECK ((flash_sale_item_id IS NULL) <> (promotion_id IS NULL))
);
CREATE INDEX idx_order_marketing_flash ON order_marketing_lines(flash_sale_item_id);
CREATE INDEX idx_notifications_status_created ON notifications(status, created_at DESC, id) WHERE NOT deleted;

INSERT INTO permissions(name, code, module) VALUES
    ('Read product reviews', 'REVIEW_READ', 'CUSTOMER'), ('Moderate product reviews', 'REVIEW_WRITE', 'CUSTOMER'),
    ('Read coupons', 'COUPON_READ', 'MARKETING'), ('Manage coupons', 'COUPON_WRITE', 'MARKETING'),
    ('Read promotions', 'PROMOTION_READ', 'MARKETING'), ('Manage promotions', 'PROMOTION_WRITE', 'MARKETING'),
    ('Read flash sales', 'FLASH_SALE_READ', 'MARKETING'), ('Manage flash sales', 'FLASH_SALE_WRITE', 'MARKETING'),
    ('Read banners', 'BANNER_READ', 'MARKETING'), ('Manage banners', 'BANNER_WRITE', 'MARKETING'),
    ('Read notifications', 'NOTIFICATION_READ', 'MARKETING'), ('Manage notifications', 'NOTIFICATION_WRITE', 'MARKETING')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN' AND NOT r.deleted AND NOT p.deleted
    AND p.code IN ('REVIEW_READ','REVIEW_WRITE','COUPON_READ','COUPON_WRITE','PROMOTION_READ','PROMOTION_WRITE',
                  'FLASH_SALE_READ','FLASH_SALE_WRITE','BANNER_READ','BANNER_WRITE','NOTIFICATION_READ','NOTIFICATION_WRITE')
ON CONFLICT (role_id, permission_id) DO NOTHING;
