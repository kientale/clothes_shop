-- Customer self-service: password reset links, saved addresses that fit the 2025 two-level
-- administrative map, and a wishlist.

-- One-time password reset links. Only the SHA-256 of the token is stored, so a database leak does not
-- hand out working links; a link is spent once and expires after a short time.
CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);
CREATE INDEX idx_password_reset_tokens_account ON password_reset_tokens(account_id);

-- Since July 2025 most addresses have only a ward and a province, so the district becomes optional.
ALTER TABLE customer_addresses ALTER COLUMN district DROP NOT NULL;
ALTER TABLE customer_addresses ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE customer_addresses ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
-- (V1 already allows at most one default address per customer: uq_customer_addresses_default.)

CREATE TABLE wishlist_items (
    customer_id UUID NOT NULL,
    product_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (customer_id, product_id),
    CONSTRAINT fk_wishlist_items_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlist_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE INDEX idx_wishlist_items_product ON wishlist_items(product_id);

CREATE TRIGGER trg_customer_addresses_updated_at BEFORE UPDATE ON customer_addresses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
