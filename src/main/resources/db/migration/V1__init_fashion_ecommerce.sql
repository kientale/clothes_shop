-- PostgreSQL 14+ / Flyway transactional migration.
-- Initial schema: 50 tables from the supplied fashion commerce data model.
-- Actor IDs reference accounts; reference_id/entity_id are polymorphic UUIDs.
-- Money uses NUMERIC(18,2). Timestamps use TIMESTAMPTZ.
-- Status/type codes are VARCHAR so application workflows can evolve.

-- Accounts, profiles and role-based access control.
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_accounts_email CHECK (email = btrim(email) AND email <> '')
);
CREATE UNIQUE INDEX uq_accounts_email_lower ON accounts (lower(email));

CREATE TABLE admin_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    avatar_url TEXT,
    CONSTRAINT uq_admin_profiles_account UNIQUE (account_id),
    CONSTRAINT fk_admin_profiles_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    CONSTRAINT uq_roles_code UNIQUE (code)
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    code VARCHAR(120) NOT NULL,
    module VARCHAR(80) NOT NULL,
    CONSTRAINT uq_permissions_code UNIQUE (code)
);
CREATE INDEX idx_permissions_module ON permissions(module);

CREATE TABLE account_roles (
    account_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (account_id, role_id),
    CONSTRAINT fk_account_roles_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_account_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);
CREATE INDEX idx_account_roles_role ON account_roles(role_id);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);

-- Customers and saved delivery addresses.
-- account_id is nullable to allow a guest customer without a login account.
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    gender VARCHAR(20),
    date_of_birth DATE,
    avatar_url TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_customers_account UNIQUE (account_id),
    CONSTRAINT fk_customers_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL
);
CREATE INDEX idx_customers_phone ON customers(phone) WHERE phone IS NOT NULL;

CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    recipient_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    province VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    ward VARCHAR(100) NOT NULL,
    address_line VARCHAR(500) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_customer_addresses_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
);
CREATE INDEX idx_customer_addresses_customer ON customer_addresses(customer_id);
CREATE UNIQUE INDEX uq_customer_addresses_default ON customer_addresses(customer_id) WHERE is_default;

-- Product reference data.
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id),
    CONSTRAINT ck_categories_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX idx_categories_parent ON categories(parent_id);

CREATE TABLE brands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    logo_url TEXT,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_brands_slug UNIQUE (slug)
);

CREATE TABLE colors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    hex_code VARCHAR(7),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_colors_code UNIQUE (code),
    CONSTRAINT ck_colors_hex CHECK (hex_code IS NULL OR hex_code ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE sizes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_sizes_code UNIQUE (code),
    CONSTRAINT ck_sizes_sort_order CHECK (sort_order >= 0)
);

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_code VARCHAR(80) NOT NULL,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(300) NOT NULL,
    description TEXT,
    short_description VARCHAR(1000),
    brand_id UUID NOT NULL,
    category_id UUID NOT NULL,
    material VARCHAR(255),
    gender VARCHAR(20) NOT NULL DEFAULT 'UNISEX',
    base_price NUMERIC(18,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_products_code UNIQUE (product_code),
    CONSTRAINT uq_products_slug UNIQUE (slug),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands(id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT ck_products_price CHECK (base_price >= 0)
);
CREATE INDEX idx_products_brand ON products(brand_id);
CREATE INDEX idx_products_category_status ON products(category_id, status);
CREATE INDEX idx_products_status_created ON products(status, created_at DESC, id);

CREATE TABLE product_variants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    color_id UUID NOT NULL,
    size_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    price NUMERIC(18,2) NOT NULL,
    compare_at_price NUMERIC(18,2),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_variants_sku UNIQUE (sku),
    CONSTRAINT uq_product_variants_attributes UNIQUE (product_id, color_id, size_id),
    CONSTRAINT uq_product_variants_id_product UNIQUE (id, product_id),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_product_variants_color FOREIGN KEY (color_id) REFERENCES colors(id),
    CONSTRAINT fk_product_variants_size FOREIGN KEY (size_id) REFERENCES sizes(id),
    CONSTRAINT ck_product_variants_price CHECK (price >= 0),
    CONSTRAINT ck_product_variants_compare_price CHECK (compare_at_price IS NULL OR compare_at_price >= price)
);
CREATE INDEX idx_product_variants_color ON product_variants(color_id);
CREATE INDEX idx_product_variants_size ON product_variants(size_id);

CREATE TABLE product_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    variant_id UUID,
    image_url TEXT NOT NULL,
    alt_text VARCHAR(255),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_images_variant_product FOREIGN KEY (variant_id, product_id)
        REFERENCES product_variants(id, product_id) ON DELETE CASCADE,
    CONSTRAINT ck_product_images_sort_order CHECK (sort_order >= 0)
);
CREATE INDEX idx_product_images_product_sort ON product_images(product_id, sort_order);
CREATE INDEX idx_product_images_variant ON product_images(variant_id) WHERE variant_id IS NOT NULL;
CREATE UNIQUE INDEX uq_product_images_product_primary ON product_images(product_id)
    WHERE is_primary AND variant_id IS NULL;
CREATE UNIQUE INDEX uq_product_images_variant_primary ON product_images(variant_id)
    WHERE is_primary AND variant_id IS NOT NULL;

CREATE TABLE collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    description TEXT,
    image_url TEXT,
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_collections_slug UNIQUE (slug),
    CONSTRAINT ck_collections_period CHECK (end_at IS NULL OR start_at IS NULL OR end_at > start_at)
);

CREATE TABLE collection_products (
    collection_id UUID NOT NULL,
    product_id UUID NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (collection_id, product_id),
    CONSTRAINT fk_collection_products_collection FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
    CONSTRAINT fk_collection_products_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT ck_collection_products_sort_order CHECK (sort_order >= 0)
);
CREATE INDEX idx_collection_products_product ON collection_products(product_id);
CREATE INDEX idx_collection_products_sort ON collection_products(collection_id, sort_order);

-- Multi-warehouse inventory. Reserved units are part of the on-hand quantity.
CREATE TABLE warehouses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    address VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE inventories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id UUID NOT NULL,
    product_variant_id UUID NOT NULL,
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    quantity_reserved INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventories_warehouse_variant UNIQUE (warehouse_id, product_variant_id),
    CONSTRAINT fk_inventories_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_inventories_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id),
    CONSTRAINT ck_inventories_on_hand CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_inventories_reserved CHECK (quantity_reserved >= 0 AND quantity_reserved <= quantity_on_hand)
);
CREATE INDEX idx_inventories_variant ON inventories(product_variant_id);

CREATE TABLE inventory_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id UUID NOT NULL,
    product_variant_id UUID NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL,
    quantity_before INTEGER NOT NULL,
    quantity_after INTEGER NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    note TEXT,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_transactions_stock FOREIGN KEY (warehouse_id, product_variant_id)
        REFERENCES inventories(warehouse_id, product_variant_id),
    CONSTRAINT fk_inventory_transactions_actor FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT ck_inventory_transactions_quantity CHECK (quantity <> 0),
    CONSTRAINT ck_inventory_transactions_before CHECK (quantity_before >= 0),
    CONSTRAINT ck_inventory_transactions_after CHECK (quantity_after >= 0)
);
CREATE INDEX idx_inventory_transactions_variant_created
    ON inventory_transactions(product_variant_id, created_at DESC);
CREATE INDEX idx_inventory_transactions_warehouse_created
    ON inventory_transactions(warehouse_id, created_at DESC);
CREATE INDEX idx_inventory_transactions_reference ON inventory_transactions(reference_type, reference_id);
CREATE INDEX idx_inventory_transactions_actor ON inventory_transactions(created_by) WHERE created_by IS NOT NULL;

-- Configurable payment/shipping methods precede the tables referencing them.
CREATE TABLE payment_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(100),
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_payment_methods_code UNIQUE (code)
);

CREATE TABLE shipping_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(100),
    base_fee NUMERIC(18,2) NOT NULL DEFAULT 0,
    estimated_days INTEGER,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_shipping_methods_code UNIQUE (code),
    CONSTRAINT ck_shipping_methods_fee CHECK (base_fee >= 0),
    CONSTRAINT ck_shipping_methods_days CHECK (estimated_days IS NULL OR estimated_days >= 0)
);

-- Orders store customer/product/address snapshots for historical consistency.
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_code VARCHAR(80) NOT NULL,
    customer_id UUID NOT NULL,
    order_status VARCHAR(30) NOT NULL DEFAULT 'PLACED',
    payment_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    shipping_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SHIPPED',
    subtotal NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    shipping_fee NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    recipient_name VARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    shipping_address TEXT NOT NULL,
    note TEXT,
    placed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_orders_code UNIQUE (order_code),
    CONSTRAINT uq_orders_id_customer UNIQUE (id, customer_id),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT ck_orders_subtotal CHECK (subtotal >= 0),
    CONSTRAINT ck_orders_discount CHECK (discount_amount >= 0 AND discount_amount <= subtotal),
    CONSTRAINT ck_orders_shipping_fee CHECK (shipping_fee >= 0),
    CONSTRAINT ck_orders_total CHECK (total_amount = subtotal - discount_amount + shipping_fee),
    CONSTRAINT ck_orders_confirmed_at CHECK (confirmed_at IS NULL OR confirmed_at >= placed_at),
    CONSTRAINT ck_orders_completed_at CHECK (completed_at IS NULL OR completed_at >= placed_at),
    CONSTRAINT ck_orders_cancelled_at CHECK (cancelled_at IS NULL OR cancelled_at >= placed_at),
    CONSTRAINT ck_orders_terminal_dates CHECK (completed_at IS NULL OR cancelled_at IS NULL)
);
CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC, id);
CREATE INDEX idx_orders_status_created ON orders(order_status, created_at DESC, id);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);
CREATE INDEX idx_orders_shipping_status ON orders(shipping_status);

CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    product_variant_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    color_name VARCHAR(100) NOT NULL,
    size_name VARCHAR(50) NOT NULL,
    unit_price NUMERIC(18,2) NOT NULL,
    quantity INTEGER NOT NULL,
    discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_items_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id),
    CONSTRAINT ck_order_items_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_discount CHECK (discount_amount >= 0 AND discount_amount <= unit_price * quantity),
    CONSTRAINT ck_order_items_total CHECK (total_amount = unit_price * quantity - discount_amount)
);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_variant ON order_items(product_variant_id);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    note TEXT,
    changed_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_status_history_actor FOREIGN KEY (changed_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT ck_order_status_history_transition CHECK (from_status IS NULL OR from_status <> to_status)
);
CREATE INDEX idx_order_status_history_order_created ON order_status_history(order_id, created_at);
CREATE INDEX idx_order_status_history_actor ON order_status_history(changed_by) WHERE changed_by IS NOT NULL;

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_payments_method FOREIGN KEY (payment_method) REFERENCES payment_methods(code),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0)
);
CREATE INDEX idx_payments_order ON payments(order_id);
CREATE INDEX idx_payments_method ON payments(payment_method);
CREATE INDEX idx_payments_status_created ON payments(status, created_at DESC);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    transaction_code VARCHAR(120) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    provider_transaction_id VARCHAR(255),
    amount NUMERIC(18,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    raw_response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_transactions_code UNIQUE (transaction_code),
    CONSTRAINT fk_payment_transactions_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT ck_payment_transactions_amount CHECK (amount >= 0)
);
CREATE INDEX idx_payment_transactions_payment_created ON payment_transactions(payment_id, created_at);
CREATE UNIQUE INDEX uq_payment_transactions_provider_id
    ON payment_transactions(provider, provider_transaction_id) WHERE provider_transaction_id IS NOT NULL;

CREATE TABLE shipments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    shipping_provider VARCHAR(100) NOT NULL,
    tracking_code VARCHAR(150),
    shipping_fee NUMERIC(18,2) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    shipped_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT ck_shipments_fee CHECK (shipping_fee >= 0),
    CONSTRAINT ck_shipments_delivery CHECK (delivered_at IS NULL OR (shipped_at IS NOT NULL AND delivered_at >= shipped_at))
);
CREATE INDEX idx_shipments_order ON shipments(order_id);
CREATE INDEX idx_shipments_status_created ON shipments(status, created_at DESC);
CREATE UNIQUE INDEX uq_shipments_provider_tracking
    ON shipments(shipping_provider, tracking_code) WHERE tracking_code IS NOT NULL;

CREATE TABLE shipment_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_shipment_status_history_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id)
);
CREATE INDEX idx_shipment_status_history_shipment_created ON shipment_status_history(shipment_id, created_at);

-- Returns, exchanges and refunds.
CREATE TABLE return_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    request_type VARCHAR(30) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    note TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_return_requests_order_customer FOREIGN KEY (order_id, customer_id) REFERENCES orders(id, customer_id),
    CONSTRAINT fk_return_requests_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT ck_return_requests_approved_at CHECK (approved_at IS NULL OR approved_at >= requested_at),
    CONSTRAINT ck_return_requests_rejected_at CHECK (rejected_at IS NULL OR rejected_at >= requested_at),
    CONSTRAINT ck_return_requests_completed_at CHECK (completed_at IS NULL OR completed_at >= requested_at),
    CONSTRAINT ck_return_requests_decision CHECK (approved_at IS NULL OR rejected_at IS NULL)
);
CREATE INDEX idx_return_requests_order ON return_requests(order_id);
CREATE INDEX idx_return_requests_customer_requested ON return_requests(customer_id, requested_at DESC);
CREATE INDEX idx_return_requests_status_requested ON return_requests(status, requested_at DESC);

CREATE TABLE return_request_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_request_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    reason TEXT,
    condition_note TEXT,
    resolution VARCHAR(30),
    CONSTRAINT uq_return_request_items_line UNIQUE (return_request_id, order_item_id),
    CONSTRAINT fk_return_request_items_request FOREIGN KEY (return_request_id) REFERENCES return_requests(id),
    CONSTRAINT fk_return_request_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT ck_return_request_items_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_return_request_items_order_item ON return_request_items(order_item_id);

CREATE TABLE return_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_request_id UUID NOT NULL,
    image_url TEXT NOT NULL,
    CONSTRAINT fk_return_images_request FOREIGN KEY (return_request_id) REFERENCES return_requests(id) ON DELETE CASCADE
);
CREATE INDEX idx_return_images_request ON return_images(return_request_id);

CREATE TABLE refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_request_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    refund_method VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    processed_by UUID,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refunds_return_request FOREIGN KEY (return_request_id) REFERENCES return_requests(id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_refunds_actor FOREIGN KEY (processed_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT ck_refunds_amount CHECK (amount > 0)
);
CREATE INDEX idx_refunds_return_request ON refunds(return_request_id);
CREATE INDEX idx_refunds_payment ON refunds(payment_id);
CREATE INDEX idx_refunds_status_created ON refunds(status, created_at DESC);
CREATE INDEX idx_refunds_actor ON refunds(processed_by) WHERE processed_by IS NOT NULL;

-- Verified purchase reviews and review images.
CREATE TABLE product_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    rating SMALLINT NOT NULL,
    comment TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_reviews_order_item UNIQUE (order_item_id),
    CONSTRAINT fk_product_reviews_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_product_reviews_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_product_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT ck_product_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX idx_product_reviews_product_status_created ON product_reviews(product_id, status, created_at DESC);
CREATE INDEX idx_product_reviews_customer ON product_reviews(customer_id);

CREATE TABLE review_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL,
    image_url TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES product_reviews(id) ON DELETE CASCADE,
    CONSTRAINT ck_review_images_sort_order CHECK (sort_order >= 0)
);
CREATE INDEX idx_review_images_review_sort ON review_images(review_id, sort_order);

-- Coupons, promotions and flash sales.
CREATE TABLE coupons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(150) NOT NULL,
    discount_type VARCHAR(30) NOT NULL,
    discount_value NUMERIC(18,2) NOT NULL,
    max_discount NUMERIC(18,2),
    minimum_order_value NUMERIC(18,2) NOT NULL DEFAULT 0,
    usage_limit INTEGER,
    usage_limit_per_customer INTEGER NOT NULL DEFAULT 1,
    used_count INTEGER NOT NULL DEFAULT 0,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_coupons_code CHECK (code = btrim(code) AND code <> ''),
    CONSTRAINT ck_coupons_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT ck_coupons_discount_value CHECK (discount_value > 0 AND (discount_type <> 'PERCENTAGE' OR discount_value <= 100)),
    CONSTRAINT ck_coupons_max_discount CHECK (max_discount IS NULL OR max_discount > 0),
    CONSTRAINT ck_coupons_minimum_order CHECK (minimum_order_value >= 0),
    CONSTRAINT ck_coupons_usage_limit CHECK (usage_limit IS NULL OR usage_limit > 0),
    CONSTRAINT ck_coupons_customer_limit CHECK (usage_limit_per_customer > 0),
    CONSTRAINT ck_coupons_used_count CHECK (used_count >= 0 AND (usage_limit IS NULL OR used_count <= usage_limit)),
    CONSTRAINT ck_coupons_period CHECK (end_at > start_at)
);
CREATE UNIQUE INDEX uq_coupons_code_lower ON coupons(lower(code));
CREATE INDEX idx_coupons_status_period ON coupons(status, start_at, end_at);

CREATE TABLE coupon_usages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    order_id UUID NOT NULL,
    discount_amount NUMERIC(18,2) NOT NULL,
    used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_coupon_usages_coupon_order UNIQUE (coupon_id, order_id),
    CONSTRAINT fk_coupon_usages_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
    CONSTRAINT fk_coupon_usages_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_coupon_usages_order_customer FOREIGN KEY (order_id, customer_id) REFERENCES orders(id, customer_id),
    CONSTRAINT ck_coupon_usages_discount CHECK (discount_amount >= 0)
);
CREATE INDEX idx_coupon_usages_customer_coupon ON coupon_usages(customer_id, coupon_id);
CREATE INDEX idx_coupon_usages_order ON coupon_usages(order_id);

CREATE TABLE promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    description TEXT,
    promotion_type VARCHAR(50) NOT NULL,
    discount_type VARCHAR(30) NOT NULL,
    discount_value NUMERIC(18,2) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_promotions_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT ck_promotions_discount_value CHECK (discount_value > 0 AND (discount_type <> 'PERCENTAGE' OR discount_value <= 100)),
    CONSTRAINT ck_promotions_priority CHECK (priority >= 0),
    CONSTRAINT ck_promotions_period CHECK (end_at > start_at)
);
CREATE INDEX idx_promotions_status_period ON promotions(status, start_at, end_at, priority);

CREATE TABLE promotion_products (
    promotion_id UUID NOT NULL,
    product_id UUID NOT NULL,
    PRIMARY KEY (promotion_id, product_id),
    CONSTRAINT fk_promotion_products_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE CASCADE,
    CONSTRAINT fk_promotion_products_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE INDEX idx_promotion_products_product ON promotion_products(product_id);

CREATE TABLE promotion_categories (
    promotion_id UUID NOT NULL,
    category_id UUID NOT NULL,
    PRIMARY KEY (promotion_id, category_id),
    CONSTRAINT fk_promotion_categories_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE CASCADE,
    CONSTRAINT fk_promotion_categories_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);
CREATE INDEX idx_promotion_categories_category ON promotion_categories(category_id);

CREATE TABLE flash_sales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_flash_sales_period CHECK (end_at > start_at)
);
CREATE INDEX idx_flash_sales_status_period ON flash_sales(status, start_at, end_at);

CREATE TABLE flash_sale_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flash_sale_id UUID NOT NULL,
    product_variant_id UUID NOT NULL,
    flash_price NUMERIC(18,2) NOT NULL,
    quantity_limit INTEGER NOT NULL,
    sold_quantity INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_flash_sale_items_variant UNIQUE (flash_sale_id, product_variant_id),
    CONSTRAINT fk_flash_sale_items_sale FOREIGN KEY (flash_sale_id) REFERENCES flash_sales(id) ON DELETE CASCADE,
    CONSTRAINT fk_flash_sale_items_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id),
    CONSTRAINT ck_flash_sale_items_price CHECK (flash_price >= 0),
    CONSTRAINT ck_flash_sale_items_limit CHECK (quantity_limit > 0),
    CONSTRAINT ck_flash_sale_items_sold CHECK (sold_quantity >= 0 AND sold_quantity <= quantity_limit)
);
CREATE INDEX idx_flash_sale_items_variant ON flash_sale_items(product_variant_id);

-- Marketing and customer notifications.
CREATE TABLE banners (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    image_url TEXT NOT NULL,
    link_url TEXT,
    position VARCHAR(50) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_banners_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_banners_period CHECK (end_at IS NULL OR start_at IS NULL OR end_at > start_at)
);
CREATE INDEX idx_banners_position_status_sort ON banners(position, status, sort_order);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_actor FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL
);
CREATE INDEX idx_notifications_created ON notifications(created_at DESC);
CREATE INDEX idx_notifications_actor ON notifications(created_by) WHERE created_by IS NOT NULL;

CREATE TABLE customer_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    CONSTRAINT uq_customer_notifications_target UNIQUE (notification_id, customer_id),
    CONSTRAINT fk_customer_notifications_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    CONSTRAINT fk_customer_notifications_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    CONSTRAINT ck_customer_notifications_read CHECK (is_read = (read_at IS NOT NULL))
);
CREATE INDEX idx_customer_notifications_customer_read ON customer_notifications(customer_id, is_read);

-- Articles and versioned store policies.
CREATE TABLE articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(300) NOT NULL,
    thumbnail_url TEXT,
    content TEXT NOT NULL,
    article_type VARCHAR(50) NOT NULL,
    author_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_articles_slug UNIQUE (slug),
    CONSTRAINT fk_articles_author FOREIGN KEY (author_id) REFERENCES accounts(id)
);
CREATE INDEX idx_articles_author ON articles(author_id);
CREATE INDEX idx_articles_type_status_published ON articles(article_type, status, published_at DESC);

CREATE TABLE article_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id UUID NOT NULL,
    image_url TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_article_images_article FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE,
    CONSTRAINT ck_article_images_sort_order CHECK (sort_order >= 0)
);
CREATE INDEX idx_article_images_article_sort ON article_images(article_id, sort_order);

CREATE TABLE store_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_policies_type_version UNIQUE (policy_type, version),
    CONSTRAINT fk_store_policies_actor FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT ck_store_policies_version CHECK (version > 0)
);
CREATE UNIQUE INDEX uq_store_policies_active_type ON store_policies(policy_type) WHERE status = 'ACTIVE';
CREATE INDEX idx_store_policies_actor ON store_policies(updated_by) WHERE updated_by IS NOT NULL;

-- System configuration and audit log.
CREATE TABLE system_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_group VARCHAR(80) NOT NULL,
    setting_key VARCHAR(120) NOT NULL,
    setting_value TEXT NOT NULL,
    data_type VARCHAR(30) NOT NULL DEFAULT 'STRING',
    description TEXT,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_system_settings_group_key UNIQUE (setting_group, setting_key),
    CONSTRAINT fk_system_settings_actor FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL
);
CREATE INDEX idx_system_settings_actor ON system_settings(updated_by) WHERE updated_by IS NOT NULL;

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    old_data JSONB,
    new_data JSONB,
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL
);
CREATE INDEX idx_audit_logs_account_created ON audit_logs(account_id, created_at DESC);
CREATE INDEX idx_audit_logs_entity_created ON audit_logs(entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at DESC);

-- Database-managed modification timestamps on tables containing updated_at.
CREATE FUNCTION set_updated_at() RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := statement_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_accounts_updated_at BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_product_variants_updated_at BEFORE UPDATE ON product_variants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_inventories_updated_at BEFORE UPDATE ON inventories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_orders_updated_at BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_product_reviews_updated_at BEFORE UPDATE ON product_reviews
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_store_policies_updated_at BEFORE UPDATE ON store_policies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_system_settings_updated_at BEFORE UPDATE ON system_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
