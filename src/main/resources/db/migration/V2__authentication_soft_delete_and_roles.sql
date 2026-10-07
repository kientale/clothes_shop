-- Preserve V1; add fields required by the authentication BaseEntity.
ALTER TABLE accounts ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE roles
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE permissions
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE customers
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TRIGGER trg_roles_updated_at BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_permissions_updated_at BEFORE UPDATE ON permissions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_customers_updated_at BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_accounts_visible_status ON accounts(status, created_at DESC, id) WHERE deleted = FALSE;
CREATE INDEX idx_customers_visible_account ON customers(account_id) WHERE deleted = FALSE;

INSERT INTO roles(name, code) VALUES ('Customer', 'CUSTOMER'), ('Administrator', 'ADMIN')
ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES
    ('Read own profile', 'AUTH_PROFILE_READ', 'AUTH'),
    ('Read account list', 'ACCOUNT_READ', 'ACCOUNT')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE (r.code = 'CUSTOMER' AND p.code = 'AUTH_PROFILE_READ')
   OR (r.code = 'ADMIN' AND p.code IN ('AUTH_PROFILE_READ', 'ACCOUNT_READ'))
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- No administrator account/password is seeded. Public registration always uses CUSTOMER.
-- Keep V1's email uniqueness across deleted accounts to prevent identity reuse.
