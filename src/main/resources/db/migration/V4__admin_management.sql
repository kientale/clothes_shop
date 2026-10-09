-- Add management capabilities without changing previously applied migrations.
ALTER TABLE admin_profiles
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TRIGGER trg_admin_profiles_updated_at BEFORE UPDATE ON admin_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Keep role codes unique even after soft deletion and regardless of casing.
CREATE UNIQUE INDEX uq_roles_code_lower ON roles(lower(code));
CREATE INDEX idx_roles_visible_created ON roles(created_at DESC, id) WHERE deleted = FALSE;
CREATE INDEX idx_customers_visible_status ON customers(status, created_at DESC, id) WHERE deleted = FALSE;

INSERT INTO permissions(name, code, module) VALUES
    ('Manage administrator accounts', 'ACCOUNT_WRITE', 'ACCOUNT'),
    ('Read roles and permissions', 'ROLE_READ', 'ROLE'),
    ('Manage roles', 'ROLE_WRITE', 'ROLE'),
    ('Read customers', 'CUSTOMER_READ', 'CUSTOMER'),
    ('Manage customers', 'CUSTOMER_WRITE', 'CUSTOMER')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND r.deleted = FALSE AND p.deleted = FALSE
  AND p.code IN ('ACCOUNT_WRITE', 'ROLE_READ', 'ROLE_WRITE', 'CUSTOMER_READ', 'CUSTOMER_WRITE')
ON CONFLICT (role_id, permission_id) DO NOTHING;
