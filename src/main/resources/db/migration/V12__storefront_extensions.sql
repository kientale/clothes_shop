-- Storefront extensions: email verification and social sign-in, guest checkout, carts kept on the server,
-- size charts, back-in-stock alerts, and the online payment (VNPay, MoMo) and carrier (GHTK) integrations.

-- Email verification. Accounts created before this migration count as verified.
ALTER TABLE accounts ADD COLUMN email_verified_at TIMESTAMPTZ;
UPDATE accounts SET email_verified_at = created_at;

-- One-time verification links; like password reset links, only the SHA-256 of the token is stored.
CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_email_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_tokens_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);
CREATE INDEX idx_email_verification_tokens_account ON email_verification_tokens(account_id);

-- Google / Facebook identities linked to an account. The subject is the provider's stable user id.
CREATE TABLE account_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email VARCHAR(254),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_account_identities_subject UNIQUE (provider, subject),
    CONSTRAINT fk_account_identities_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    CONSTRAINT ck_account_identities_provider CHECK (provider IN ('GOOGLE', 'FACEBOOK'))
);
CREATE INDEX idx_account_identities_account ON account_identities(account_id);

-- Guest checkout: the order keeps the email for its confirmation; guests are customers without an account.
ALTER TABLE orders ADD COLUMN contact_email VARCHAR(254);
CREATE INDEX idx_customers_guest_phone ON customers(phone) WHERE account_id IS NULL AND NOT deleted;

-- A signed-in customer's cart, so it follows them across devices. Prices are never stored here.
CREATE TABLE cart_items (
    customer_id UUID NOT NULL,
    product_variant_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (customer_id, product_variant_id),
    CONSTRAINT fk_cart_items_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 1000)
);
CREATE TRIGGER trg_cart_items_updated_at BEFORE UPDATE ON cart_items FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Size chart of a product: {"columns": [...], "rows": [[...], ...], "note": "..."}.
CREATE TABLE product_size_charts (
    product_id UUID PRIMARY KEY,
    chart JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_size_charts_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE TRIGGER trg_product_size_charts_updated_at BEFORE UPDATE ON product_size_charts FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- "Email me when it is back": one open request per variant and email; notified_at closes it.
CREATE TABLE stock_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_variant_id UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    customer_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notified_at TIMESTAMPTZ,
    CONSTRAINT fk_stock_alerts_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_alerts_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
    CONSTRAINT ck_stock_alerts_email CHECK (email = btrim(email) AND email <> '')
);
CREATE UNIQUE INDEX uq_stock_alerts_open ON stock_alerts(product_variant_id, lower(email)) WHERE notified_at IS NULL;
CREATE INDEX idx_stock_alerts_created ON stock_alerts(created_at DESC);

-- Gateway transactions are looked up by their reference when the gateway calls back.
CREATE INDEX idx_payment_transactions_provider_code ON payment_transactions(provider, transaction_code);

-- Online payment methods and the GHTK carrier stay off until their keys are configured and an
-- administrator switches them on.
INSERT INTO payment_methods(code, name, provider, configuration, is_enabled) VALUES
    ('VNPAY', 'VNPay (thẻ ATM, Visa/Master, QR ngân hàng)', 'VNPAY', '{"kind": "VNPAY", "bankDetails": null, "instructions": null}', FALSE),
    ('MOMO', 'Ví MoMo', 'MOMO', '{"kind": "MOMO", "bankDetails": null, "instructions": null}', FALSE)
ON CONFLICT (code) DO NOTHING;
INSERT INTO shipping_methods(code, name, provider, base_fee, estimated_days, is_enabled) VALUES
    ('GHTK', 'Giao Hàng Tiết Kiệm', 'GHTK', 30000, 3, FALSE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions(name, code, module) VALUES
    ('Read back-in-stock requests', 'STOCK_ALERT_READ', 'INVENTORY')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND NOT r.deleted AND NOT p.deleted AND p.code = 'STOCK_ALERT_READ'
ON CONFLICT (role_id, permission_id) DO NOTHING;
