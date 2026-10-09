ALTER TABLE system_settings ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0);
ALTER TABLE payment_methods ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE TRIGGER trg_payment_methods_updated_at BEFORE UPDATE ON payment_methods FOR EACH ROW EXECUTE FUNCTION set_updated_at();
ALTER TABLE shipping_methods ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE TRIGGER trg_shipping_methods_updated_at BEFORE UPDATE ON shipping_methods FOR EACH ROW EXECUTE FUNCTION set_updated_at();
UPDATE payment_methods SET configuration = jsonb_build_object('kind', code, 'bankDetails', NULL, 'instructions', NULL)
    WHERE code IN ('COD', 'BANK_TRANSFER') AND provider = 'MANUAL' AND configuration = '{}'::jsonb;
ALTER TABLE orders ADD COLUMN shipping_method_code VARCHAR(50) REFERENCES shipping_methods(code) ON DELETE RESTRICT;
ALTER TABLE orders ADD COLUMN shipping_fee_configured BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_orders_shipping_method ON orders(shipping_method_code);
INSERT INTO system_settings(setting_group, setting_key, setting_value, data_type, description) VALUES ('STORE', 'CONFIG', '{"storeName":"LemonadeX","legalName":null,"taxCode":null,"supportEmail":null,"supportPhone":null,"address":null,"logoUrl":null,"websiteUrl":null,"businessHours":null}', 'JSON', 'Typed store settings') ON CONFLICT (setting_group, setting_key) DO NOTHING;
INSERT INTO system_settings(setting_group, setting_key, setting_value, data_type, description) VALUES ('PAYMENT', 'CONFIG', '{"paymentsEnabled":true,"allowPartialPayments":true,"minimumPaymentAmount":0.01}', 'JSON', 'Typed payment settings') ON CONFLICT (setting_group, setting_key) DO NOTHING;
INSERT INTO system_settings(setting_group, setting_key, setting_value, data_type, description) VALUES ('SHIPPING', 'CONFIG', '{"shippingEnabled":true,"useConfiguredFees":false,"defaultBaseFee":0,"freeShippingThreshold":null}', 'JSON', 'Typed shipping settings') ON CONFLICT (setting_group, setting_key) DO NOTHING;
INSERT INTO system_settings(setting_group, setting_key, setting_value, data_type, description) VALUES ('ORDER', 'CONFIG', '{"ordersEnabled":true,"autoConfirm":false,"orderCodePrefix":"LX","minimumOrderAmount":0,"maxItems":100,"maxQuantityPerItem":1000000,"enableMarketingByDefault":false,"allowCancellation":true,"allowReturns":true,"returnWindowDays":null}', 'JSON', 'Typed order settings') ON CONFLICT (setting_group, setting_key) DO NOTHING;
INSERT INTO system_settings(setting_group, setting_key, setting_value, data_type, description) VALUES ('NOTIFICATION', 'CONFIG', '{"inAppEnabled":true,"allowBroadcast":true,"maxRecipients":1000000}', 'JSON', 'Typed notification settings') ON CONFLICT (setting_group, setting_key) DO NOTHING;
INSERT INTO system_settings(setting_group, setting_key, setting_value, data_type, description) VALUES ('GENERAL', 'CONFIG', '{"timezone":"Asia/Ho_Chi_Minh","language":"VI","maintenanceMode":false,"maintenanceMessage":null}', 'JSON', 'Typed general settings') ON CONFLICT (setting_group, setting_key) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Read store settings', 'SETTINGS_STORE_READ', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Write store settings', 'SETTINGS_STORE_WRITE', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Read payment settings', 'SETTINGS_PAYMENT_READ', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Write payment settings', 'SETTINGS_PAYMENT_WRITE', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Read shipping settings', 'SETTINGS_SHIPPING_READ', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Write shipping settings', 'SETTINGS_SHIPPING_WRITE', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Read order settings', 'SETTINGS_ORDER_READ', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Write order settings', 'SETTINGS_ORDER_WRITE', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Read notification settings', 'SETTINGS_NOTIFICATION_READ', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Write notification settings', 'SETTINGS_NOTIFICATION_WRITE', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Read general settings', 'SETTINGS_GENERAL_READ', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(name, code, module) VALUES ('Write general settings', 'SETTINGS_GENERAL_WRITE', 'SETTINGS') ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions(role_id, permission_id) SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN' AND NOT r.deleted AND NOT p.deleted AND p.code IN ('SETTINGS_STORE_READ','SETTINGS_STORE_WRITE','SETTINGS_PAYMENT_READ','SETTINGS_PAYMENT_WRITE','SETTINGS_SHIPPING_READ','SETTINGS_SHIPPING_WRITE','SETTINGS_ORDER_READ','SETTINGS_ORDER_WRITE','SETTINGS_NOTIFICATION_READ','SETTINGS_NOTIFICATION_WRITE','SETTINGS_GENERAL_READ','SETTINGS_GENERAL_WRITE') ON CONFLICT (role_id, permission_id) DO NOTHING;
