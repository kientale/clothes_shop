-- Run only on a disposable PostgreSQL database after V1.
-- All fixtures are rolled back; unexpected success or schema drift aborts.
BEGIN;

DO $verification$
DECLARE
    expected JSONB := '{
  "accounts": [
    "id",
    "email",
    "password_hash",
    "status",
    "last_login_at",
    "created_at",
    "updated_at"
  ],
  "admin_profiles": [
    "id",
    "account_id",
    "full_name",
    "phone",
    "avatar_url"
  ],
  "roles": [
    "id",
    "name",
    "code"
  ],
  "permissions": [
    "id",
    "name",
    "code",
    "module"
  ],
  "account_roles": [
    "account_id",
    "role_id"
  ],
  "role_permissions": [
    "role_id",
    "permission_id"
  ],
  "products": [
    "id",
    "product_code",
    "name",
    "slug",
    "description",
    "short_description",
    "brand_id",
    "category_id",
    "material",
    "gender",
    "base_price",
    "status",
    "created_at",
    "updated_at"
  ],
  "categories": [
    "id",
    "parent_id",
    "name",
    "slug",
    "description",
    "status"
  ],
  "brands": [
    "id",
    "name",
    "slug",
    "logo_url",
    "description",
    "status"
  ],
  "colors": [
    "id",
    "name",
    "code",
    "hex_code",
    "status"
  ],
  "sizes": [
    "id",
    "name",
    "code",
    "sort_order",
    "status"
  ],
  "product_variants": [
    "id",
    "product_id",
    "color_id",
    "size_id",
    "sku",
    "price",
    "compare_at_price",
    "status",
    "created_at",
    "updated_at"
  ],
  "product_images": [
    "id",
    "product_id",
    "variant_id",
    "image_url",
    "alt_text",
    "is_primary",
    "sort_order"
  ],
  "collections": [
    "id",
    "name",
    "slug",
    "description",
    "image_url",
    "start_at",
    "end_at",
    "status"
  ],
  "collection_products": [
    "collection_id",
    "product_id",
    "sort_order"
  ],
  "warehouses": [
    "id",
    "name",
    "address",
    "status"
  ],
  "inventories": [
    "id",
    "warehouse_id",
    "product_variant_id",
    "quantity_on_hand",
    "quantity_reserved",
    "updated_at"
  ],
  "inventory_transactions": [
    "id",
    "warehouse_id",
    "product_variant_id",
    "transaction_type",
    "quantity",
    "quantity_before",
    "quantity_after",
    "reference_type",
    "reference_id",
    "note",
    "created_by",
    "created_at"
  ],
  "orders": [
    "id",
    "order_code",
    "customer_id",
    "order_status",
    "payment_status",
    "shipping_status",
    "subtotal",
    "discount_amount",
    "shipping_fee",
    "total_amount",
    "recipient_name",
    "recipient_phone",
    "shipping_address",
    "note",
    "placed_at",
    "confirmed_at",
    "completed_at",
    "cancelled_at",
    "created_at",
    "updated_at"
  ],
  "order_items": [
    "id",
    "order_id",
    "product_variant_id",
    "product_name",
    "sku",
    "color_name",
    "size_name",
    "unit_price",
    "quantity",
    "discount_amount",
    "total_amount"
  ],
  "order_status_history": [
    "id",
    "order_id",
    "from_status",
    "to_status",
    "note",
    "changed_by",
    "created_at"
  ],
  "payments": [
    "id",
    "order_id",
    "payment_method",
    "amount",
    "status",
    "paid_at",
    "created_at"
  ],
  "payment_transactions": [
    "id",
    "payment_id",
    "transaction_code",
    "provider",
    "provider_transaction_id",
    "amount",
    "status",
    "raw_response",
    "created_at"
  ],
  "shipments": [
    "id",
    "order_id",
    "shipping_provider",
    "tracking_code",
    "shipping_fee",
    "status",
    "shipped_at",
    "delivered_at",
    "created_at"
  ],
  "shipment_status_history": [
    "id",
    "shipment_id",
    "status",
    "description",
    "created_at"
  ],
  "return_requests": [
    "id",
    "order_id",
    "customer_id",
    "request_type",
    "reason",
    "status",
    "note",
    "requested_at",
    "approved_at",
    "rejected_at",
    "completed_at"
  ],
  "return_request_items": [
    "id",
    "return_request_id",
    "order_item_id",
    "quantity",
    "reason",
    "condition_note",
    "resolution"
  ],
  "return_images": [
    "id",
    "return_request_id",
    "image_url"
  ],
  "refunds": [
    "id",
    "return_request_id",
    "payment_id",
    "amount",
    "refund_method",
    "status",
    "reason",
    "processed_by",
    "processed_at",
    "created_at"
  ],
  "customers": [
    "id",
    "account_id",
    "full_name",
    "phone",
    "gender",
    "date_of_birth",
    "avatar_url",
    "status",
    "created_at"
  ],
  "customer_addresses": [
    "id",
    "customer_id",
    "recipient_name",
    "phone",
    "province",
    "district",
    "ward",
    "address_line",
    "is_default"
  ],
  "product_reviews": [
    "id",
    "product_id",
    "customer_id",
    "order_item_id",
    "rating",
    "comment",
    "status",
    "created_at",
    "updated_at"
  ],
  "review_images": [
    "id",
    "review_id",
    "image_url",
    "sort_order"
  ],
  "coupons": [
    "id",
    "code",
    "name",
    "discount_type",
    "discount_value",
    "max_discount",
    "minimum_order_value",
    "usage_limit",
    "usage_limit_per_customer",
    "used_count",
    "start_at",
    "end_at",
    "status"
  ],
  "coupon_usages": [
    "id",
    "coupon_id",
    "customer_id",
    "order_id",
    "discount_amount",
    "used_at"
  ],
  "promotions": [
    "id",
    "name",
    "description",
    "promotion_type",
    "discount_type",
    "discount_value",
    "start_at",
    "end_at",
    "priority",
    "status"
  ],
  "promotion_products": [
    "promotion_id",
    "product_id"
  ],
  "promotion_categories": [
    "promotion_id",
    "category_id"
  ],
  "flash_sales": [
    "id",
    "name",
    "start_at",
    "end_at",
    "status"
  ],
  "flash_sale_items": [
    "id",
    "flash_sale_id",
    "product_variant_id",
    "flash_price",
    "quantity_limit",
    "sold_quantity"
  ],
  "banners": [
    "id",
    "title",
    "image_url",
    "link_url",
    "position",
    "sort_order",
    "start_at",
    "end_at",
    "status"
  ],
  "notifications": [
    "id",
    "title",
    "content",
    "notification_type",
    "target_type",
    "created_by",
    "created_at"
  ],
  "customer_notifications": [
    "id",
    "notification_id",
    "customer_id",
    "is_read",
    "read_at"
  ],
  "articles": [
    "id",
    "title",
    "slug",
    "thumbnail_url",
    "content",
    "article_type",
    "author_id",
    "status",
    "published_at",
    "created_at"
  ],
  "article_images": [
    "id",
    "article_id",
    "image_url",
    "sort_order"
  ],
  "store_policies": [
    "id",
    "policy_type",
    "title",
    "content",
    "version",
    "status",
    "updated_by",
    "updated_at"
  ],
  "system_settings": [
    "id",
    "setting_group",
    "setting_key",
    "setting_value",
    "data_type",
    "description",
    "updated_by",
    "updated_at"
  ],
  "payment_methods": [
    "id",
    "code",
    "name",
    "provider",
    "configuration",
    "is_enabled"
  ],
  "shipping_methods": [
    "id",
    "code",
    "name",
    "provider",
    "base_fee",
    "estimated_days",
    "is_enabled"
  ],
  "audit_logs": [
    "id",
    "account_id",
    "action",
    "entity_type",
    "entity_id",
    "old_data",
    "new_data",
    "ip_address",
    "created_at"
  ]
}'::jsonb;
    entry RECORD;
    expected_columns TEXT[];
    actual_columns TEXT[];
    actual_table_count INTEGER;
    account UUID := gen_random_uuid();
    customer UUID := gen_random_uuid();
    stranger UUID := gen_random_uuid();
    category UUID := gen_random_uuid();
    brand UUID := gen_random_uuid();
    color UUID := gen_random_uuid();
    size UUID := gen_random_uuid();
    product UUID := gen_random_uuid();
    other_product UUID := gen_random_uuid();
    variant UUID := gen_random_uuid();
    warehouse UUID := gen_random_uuid();
    stock UUID := gen_random_uuid();
    customer_order UUID := gen_random_uuid();
    order_item UUID := gen_random_uuid();
    payment UUID := gen_random_uuid();
    sale UUID := gen_random_uuid();
    sale_item UUID := gen_random_uuid();
    notification UUID := gen_random_uuid();
BEGIN
    SELECT count(*) INTO actual_table_count FROM information_schema.tables
    WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
      AND table_name <> 'flyway_schema_history';
    IF actual_table_count <> 50 THEN
        RAISE EXCEPTION 'Expected 50 tables, found %', actual_table_count;
    END IF;

    FOR entry IN SELECT key, value FROM jsonb_each(expected) LOOP
        SELECT array_agg(value ORDER BY value) INTO expected_columns
            FROM jsonb_array_elements_text(entry.value);
        SELECT array_agg(column_name::TEXT ORDER BY column_name) INTO actual_columns
            FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = entry.key;
        IF actual_columns IS DISTINCT FROM expected_columns THEN
            RAISE EXCEPTION 'Columns for % differ: expected %, got %', entry.key, expected_columns, actual_columns;
        END IF;
    END LOOP;

    INSERT INTO accounts(id, email, password_hash, updated_at)
        VALUES (account, 'migration-check@example.com', 'test-hash', '2000-01-01 00:00:00+00');
    BEGIN
        INSERT INTO accounts(email, password_hash) VALUES ('MIGRATION-CHECK@example.com', 'test-hash');
        RAISE EXCEPTION 'Case-insensitive email uniqueness was not enforced';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    UPDATE accounts SET status = 'INACTIVE' WHERE id = account;
    IF (SELECT updated_at FROM accounts WHERE id = account) <= '2000-01-01 00:00:00+00'::TIMESTAMPTZ THEN
        RAISE EXCEPTION 'updated_at trigger did not run';
    END IF;

    INSERT INTO customers(id, account_id, full_name) VALUES (customer, account, 'Test customer');
    INSERT INTO customers(id, full_name) VALUES (stranger, 'Other customer');
    INSERT INTO customer_addresses(customer_id, recipient_name, phone, province, district, ward, address_line, is_default)
        VALUES (customer, 'Test customer', '0901234567', 'City', 'District', 'Ward', 'Address', TRUE);
    BEGIN
        INSERT INTO customer_addresses(customer_id, recipient_name, phone, province, district, ward, address_line, is_default)
            VALUES (customer, 'Test customer', '0901234567', 'City', 'District', 'Ward', 'Another address', TRUE);
        RAISE EXCEPTION 'Multiple default addresses were accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    INSERT INTO categories(id, name, slug) VALUES (category, 'Shirts', 'migration-shirts');
    INSERT INTO brands(id, name, slug) VALUES (brand, 'Test brand', 'migration-brand');
    INSERT INTO colors(id, name, code, hex_code) VALUES (color, 'Black', 'MIGRATION_BLACK', '#000000');
    INSERT INTO sizes(id, name, code) VALUES (size, 'Medium', 'MIGRATION_M');
    INSERT INTO products(id, product_code, name, slug, brand_id, category_id, base_price)
        VALUES (product, 'MIGRATION-P1', 'Test shirt', 'migration-shirt', brand, category, 100),
               (other_product, 'MIGRATION-P2', 'Other shirt', 'migration-other-shirt', brand, category, 100);
    INSERT INTO product_variants(id, product_id, color_id, size_id, sku, price, compare_at_price)
        VALUES (variant, product, color, size, 'MIGRATION-SKU', 100, 120);
    INSERT INTO product_images(product_id, variant_id, image_url, is_primary)
        VALUES (product, variant, 'https://example.com/variant.jpg', TRUE);
    BEGIN
        INSERT INTO product_images(product_id, variant_id, image_url)
            VALUES (other_product, variant, 'https://example.com/wrong-product.jpg');
        RAISE EXCEPTION 'A variant image was attached to a different product';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO product_images(product_id, variant_id, image_url, is_primary)
            VALUES (product, variant, 'https://example.com/duplicate-primary.jpg', TRUE);
        RAISE EXCEPTION 'Multiple primary images for one variant were accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        UPDATE product_variants SET compare_at_price = 99 WHERE id = variant;
        RAISE EXCEPTION 'Compare-at price below selling price was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    INSERT INTO warehouses(id, name, address) VALUES (warehouse, 'Test warehouse', 'Warehouse address');
    INSERT INTO inventories(id, warehouse_id, product_variant_id, quantity_on_hand, quantity_reserved)
        VALUES (stock, warehouse, variant, 10, 2);
    INSERT INTO inventory_transactions(warehouse_id, product_variant_id, transaction_type, quantity,
                                      quantity_before, quantity_after, created_by)
        VALUES (warehouse, variant, 'INBOUND', 10, 0, 10, account);
    BEGIN
        UPDATE inventories SET quantity_reserved = 11 WHERE id = stock;
        RAISE EXCEPTION 'Reservation exceeded on-hand inventory';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE inventories SET quantity_on_hand = -1 WHERE id = stock;
        RAISE EXCEPTION 'Negative on-hand inventory was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    INSERT INTO orders(id, order_code, customer_id, subtotal, discount_amount, shipping_fee, total_amount,
                       recipient_name, recipient_phone, shipping_address)
        VALUES (customer_order, 'MIGRATION-ORDER', customer, 200, 50, 25, 175,
                'Test customer', '0901234567', 'Shipping address snapshot');
    INSERT INTO order_items(id, order_id, product_variant_id, product_name, sku, color_name, size_name,
                            unit_price, quantity, discount_amount, total_amount)
        VALUES (order_item, customer_order, variant, 'Test shirt snapshot', 'MIGRATION-SKU', 'Black', 'Medium',
                100, 2, 50, 150);
    BEGIN
        UPDATE orders SET total_amount = 1 WHERE id = customer_order;
        RAISE EXCEPTION 'Incorrect order total was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE order_items SET total_amount = 1 WHERE id = order_item;
        RAISE EXCEPTION 'Incorrect order item total was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO return_requests(order_id, customer_id, request_type, reason)
            VALUES (customer_order, stranger, 'RETURN', 'Wrong owner');
        RAISE EXCEPTION 'A return request from another customer was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    INSERT INTO product_reviews(product_id, customer_id, order_item_id, rating)
        VALUES (product, customer, order_item, 5);
    BEGIN
        UPDATE product_reviews SET rating = 6 WHERE order_item_id = order_item;
        RAISE EXCEPTION 'Out-of-range review rating was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    INSERT INTO payment_methods(code, name, is_enabled) VALUES ('MIGRATION_COD', 'Test COD', TRUE);
    INSERT INTO payments(id, order_id, payment_method, amount) VALUES (payment, customer_order, 'MIGRATION_COD', 175);
    INSERT INTO payment_transactions(payment_id, transaction_code, provider, provider_transaction_id, amount, raw_response)
        VALUES (payment, 'MIGRATION-TX1', 'TEST_PROVIDER', 'external-123', 175, '{"ok":true}'::JSONB);
    BEGIN
        INSERT INTO payment_transactions(payment_id, transaction_code, provider, provider_transaction_id, amount)
            VALUES (payment, 'MIGRATION-TX2', 'TEST_PROVIDER', 'external-123', 175);
        RAISE EXCEPTION 'Duplicate provider transaction was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO coupons(code, name, discount_type, discount_value, start_at, end_at)
            VALUES ('MIGRATION101', 'Invalid percentage', 'PERCENTAGE', 101, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day');
        RAISE EXCEPTION 'Coupon percentage above 100 was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO promotions(name, promotion_type, discount_type, discount_value, start_at, end_at)
            VALUES ('Invalid period', 'PRODUCT_DISCOUNT', 'PERCENTAGE', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '1 day');
        RAISE EXCEPTION 'Invalid promotion period was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    INSERT INTO flash_sales(id, name, start_at, end_at)
        VALUES (sale, 'Test flash sale', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day');
    INSERT INTO flash_sale_items(id, flash_sale_id, product_variant_id, flash_price, quantity_limit, sold_quantity)
        VALUES (sale_item, sale, variant, 80, 5, 3);
    BEGIN
        UPDATE flash_sale_items SET sold_quantity = 6 WHERE id = sale_item;
        RAISE EXCEPTION 'Flash sale sales exceeded the quantity limit';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    INSERT INTO notifications(id, title, content, notification_type, target_type, created_by)
        VALUES (notification, 'Test', 'Test content', 'SYSTEM', 'CUSTOMER', account);
    INSERT INTO customer_notifications(notification_id, customer_id)
        VALUES (notification, customer);
    BEGIN
        UPDATE customer_notifications SET is_read = TRUE WHERE notification_id = notification;
        RAISE EXCEPTION 'Read notification without read_at was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    UPDATE customer_notifications SET is_read = TRUE, read_at = CURRENT_TIMESTAMP WHERE notification_id = notification;

    RAISE NOTICE 'Verified all 50 tables and exact columns, valid relational inserts, timestamps, and 16 rejected invalid writes.';
END;
$verification$;

ROLLBACK;
