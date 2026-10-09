-- Preserve V1 commerce records. Financial records and movement/history rows are never deleted by APIs.
ALTER TABLE warehouses ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE TRIGGER trg_warehouses_updated_at BEFORE UPDATE ON warehouses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
ALTER TABLE inventories ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE inventory_transactions ADD COLUMN reserved_before INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reserved_after INTEGER NOT NULL DEFAULT 0;
ALTER TABLE inventory_transactions ADD CONSTRAINT ck_inventory_transactions_reserved
    CHECK (reserved_before >= 0 AND reserved_after >= 0);

-- Allocation is recorded at checkout; nullable only for orders predating this migration.
ALTER TABLE orders ADD COLUMN warehouse_id UUID REFERENCES warehouses(id);
ALTER TABLE order_items ADD COLUMN inventory_id UUID REFERENCES inventories(id);
ALTER TABLE return_request_items ADD COLUMN replacement_variant_id UUID REFERENCES product_variants(id),
    ADD COLUMN replacement_inventory_id UUID REFERENCES inventories(id);

CREATE INDEX idx_inventories_warehouse_updated ON inventories(warehouse_id, updated_at DESC, id);
CREATE INDEX idx_inventory_transactions_type_created ON inventory_transactions(transaction_type, created_at DESC, id);
CREATE INDEX idx_refunds_payment_status ON refunds(payment_id, status);
CREATE INDEX idx_refunds_return_status ON refunds(return_request_id, status);

INSERT INTO payment_methods(code, name, provider, is_enabled) VALUES
    ('COD', 'Cash on delivery', 'MANUAL', TRUE),
    ('BANK_TRANSFER', 'Bank transfer', 'MANUAL', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions(name, code, module) VALUES
    ('Read inventory', 'INVENTORY_READ', 'INVENTORY'), ('Manage inventory', 'INVENTORY_WRITE', 'INVENTORY'),
    ('Read orders', 'ORDER_READ', 'ORDER'), ('Manage orders', 'ORDER_WRITE', 'ORDER'),
    ('Read payments', 'PAYMENT_READ', 'ORDER'), ('Manage payments', 'PAYMENT_WRITE', 'ORDER'),
    ('Read shipments', 'SHIPMENT_READ', 'ORDER'), ('Manage shipments', 'SHIPMENT_WRITE', 'ORDER'),
    ('Read returns', 'RETURN_READ', 'ORDER'), ('Manage returns', 'RETURN_WRITE', 'ORDER'),
    ('Read refunds', 'REFUND_READ', 'ORDER'), ('Manage refunds', 'REFUND_WRITE', 'ORDER')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND NOT r.deleted AND NOT p.deleted
    AND p.code IN ('INVENTORY_READ','INVENTORY_WRITE','ORDER_READ','ORDER_WRITE','PAYMENT_READ','PAYMENT_WRITE',
                  'SHIPMENT_READ','SHIPMENT_WRITE','RETURN_READ','RETURN_WRITE','REFUND_READ','REFUND_WRITE')
ON CONFLICT (role_id, permission_id) DO NOTHING;
