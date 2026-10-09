package lemonadex.project.clothes;

import lemonadex.project.clothes.features.auth.dto.*;
import lemonadex.project.clothes.features.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.Instant;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class SettingsIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    private static final String API = "/api/v1/admin";
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private String token;
    private UUID customer, warehouse, product, variant, replacement, stock, replacementStock;

    private List<Map<String, Object>> settingsBefore, paymentBefore;
    private List<UUID> auditBefore;

    @BeforeEach
    void fixtures() throws Exception {
        settingsBefore = jdbc.queryForList("select setting_group, setting_value, revision, updated_by from system_settings where setting_key = 'CONFIG'");
        paymentBefore = jdbc.queryForList("select id, configuration::text as configuration, is_enabled, revision, deleted from payment_methods");
        auditBefore = jdbc.queryForList("select id from audit_logs where entity_type like 'SETTINGS_%'", UUID.class);
        token = auth.login(new LoginRequest("admin", "admin123")).accessToken();
        customer = id(create("/customers", map("fullName", "Commerce " + tag, "status", "ACTIVE")));
        UUID category = id(create("/categories", map("name", "Commerce " + tag, "status", "ACTIVE")));
        UUID brand = id(create("/brands", map("name", "Commerce " + tag, "status", "ACTIVE")));
        UUID color = id(create("/colors", map("name", "Commerce " + tag, "code", "C-" + tag, "status", "ACTIVE")));
        UUID small = id(create("/sizes", map("name", "S " + tag, "code", "S-" + tag, "sortOrder", 1, "status", "ACTIVE")));
        UUID medium = id(create("/sizes", map("name", "M " + tag, "code", "M-" + tag, "sortOrder", 2, "status", "ACTIVE")));
        product = id(create("/products", map("productCode", "P-" + tag, "name", "Commerce " + tag, "brandId", brand,
                "categoryId", category, "gender", "UNISEX", "basePrice", 100, "status", "ACTIVE", "images", List.of())));
        variant = id(create("/product-variants", map("productId", product, "colorId", color, "sizeId", small, "price", 100, "status", "ACTIVE")));
        replacement = id(create("/product-variants", map("productId", product, "colorId", color, "sizeId", medium, "price", 100, "status", "ACTIVE")));
        warehouse = id(create("/warehouses", map("name", "Warehouse " + tag, "address", "Hồ Chí Minh", "status", "ACTIVE")));
        stock = id(create("/inventory", map("warehouseId", warehouse, "productVariantId", variant)));
        replacementStock = id(create("/inventory", map("warehouseId", warehouse, "productVariantId", replacement)));
        movement(variant, "RECEIPT", 10).andExpect(status().isCreated());
        movement(replacement, "RECEIPT", 10).andExpect(status().isCreated());
        customerAccounts();
    }

    private String customerToken, otherToken;
    private UUID customerAccount, otherAccount, otherCustomer;
    void customerAccounts() {
        var one = auth.register(new RegisterRequest("m-" + tag + "@example.com", "Password-1234", "Password-1234", "Marketing " + tag, null, null));
        var two = auth.register(new RegisterRequest("n-" + tag + "@example.com", "Password-1234", "Password-1234", "Other " + tag, null, null));
        customerToken = one.accessToken(); otherToken = two.accessToken(); customerAccount = one.account().id(); otherAccount = two.account().id();
        otherCustomer = jdbc.queryForObject("select id from customers where account_id = ?", UUID.class, otherAccount);
        // The commerce fixture customer uses the real customer's account, with no duplicate profile.
        jdbc.update("delete from customers where account_id = ?", customerAccount);
        jdbc.update("update customers set account_id = ? where id = ?", customerAccount, customer);
    }

    @AfterEach
    void cleanup() {
        for (var r : settingsBefore) jdbc.update("update system_settings set setting_value = ?, revision = ?, updated_by = ? where setting_group = ? and setting_key = 'CONFIG'", r.get("setting_value"), r.get("revision"), r.get("updated_by"), r.get("setting_group"));
        for (var r : paymentBefore) jdbc.update("update payment_methods set configuration = cast(? as jsonb), is_enabled = ?, revision = ?, deleted = ? where id = ?", r.get("configuration"), r.get("is_enabled"), r.get("revision"), r.get("deleted"), r.get("id"));
        for (UUID id : jdbc.queryForList("select id from audit_logs where entity_type like 'SETTINGS_%'", UUID.class)) if (!auditBefore.contains(id)) jdbc.update("delete from audit_logs where id = ?", id);
        // Remove only this test's fixtures, in foreign-key order; API records themselves remain durable.
        String orders = "select id from orders where customer_id in (select id from customers where full_name like ?)";
        String returned = "select id from return_requests where order_id in (" + orders + ")";
        String payments = "select id from payments where order_id in (" + orders + ")";
        String shipments = "select id from shipments where order_id in (" + orders + ")";
        String like = "%" + tag + "%";
        jdbc.update("delete from notifications where title like ?", like);
        jdbc.update("delete from review_images where review_id in (select id from product_reviews where customer_id in (select id from customers where full_name like ?))", like);
        jdbc.update("delete from product_reviews where customer_id in (select id from customers where full_name like ?)", like);
        jdbc.update("delete from coupon_usages where order_id in (" + orders + ")", like);
        jdbc.update("delete from order_marketing_lines where order_item_id in (select id from order_items where order_id in (" + orders + "))", like);
        jdbc.update("delete from flash_sale_items where flash_sale_id in (select id from flash_sales where name like ?)", like);
        jdbc.update("delete from flash_sales where name like ?", like);
        jdbc.update("delete from coupons where name like ?", like);
        jdbc.update("delete from promotions where name like ?", like);
        jdbc.update("delete from banners where title like ?", like);
        jdbc.update("delete from refunds where return_request_id in (" + returned + ")", like);
        jdbc.update("delete from return_images where return_request_id in (" + returned + ")", like);
        jdbc.update("delete from return_request_items where return_request_id in (" + returned + ")", like);
        jdbc.update("delete from return_requests where order_id in (" + orders + ")", like);
        jdbc.update("delete from shipment_status_history where shipment_id in (" + shipments + ")", like);
        jdbc.update("delete from shipments where order_id in (" + orders + ")", like);
        jdbc.update("delete from payment_transactions where payment_id in (" + payments + ")", like);
        jdbc.update("delete from payments where order_id in (" + orders + ")", like);
        jdbc.update("delete from order_status_history where order_id in (" + orders + ")", like);
        jdbc.update("delete from order_items where order_id in (" + orders + ")", like);
        jdbc.update("delete from orders where customer_id in (select id from customers where full_name like ?)", like);
        jdbc.update("delete from inventory_transactions where warehouse_id in (select id from warehouses where name like ?)", like);
        jdbc.update("delete from inventories where warehouse_id in (select id from warehouses where name like ?)", like);
        jdbc.update("delete from warehouses where name like ?", like);
        jdbc.update("delete from product_images where product_id in (select id from products where name like ?)", like);
        jdbc.update("delete from product_variants where product_id in (select id from products where name like ?)", like);
        jdbc.update("delete from products where name like ?", like);
        jdbc.update("delete from colors where name like ?", like);
        jdbc.update("delete from sizes where name like ?", like);
        jdbc.update("delete from brands where name like ?", like);
        jdbc.update("delete from categories where name like ?", like);
        jdbc.update("delete from customers where full_name like ?", like);
        jdbc.update("delete from payment_methods where code like ?", "%" + tag.toUpperCase(Locale.ROOT) + "%");
        jdbc.update("delete from shipping_methods where code like ?", "%" + tag.toUpperCase(Locale.ROOT) + "%");
        if (customerAccount != null) jdbc.update("delete from accounts where id = ?", customerAccount);
        if (otherAccount != null) jdbc.update("delete from accounts where id = ?", otherAccount);
    }

    @Test
    void allSixGroupsPersistTypedConfigurationAndActorWithAudit() throws Exception {
        for (String group : List.of("store", "payment", "shipping", "order", "notification", "general")) {
            var before = data(perform(get(API + "/settings/" + group), null).andExpect(status().isOk()));
            Object c = json.readValue(before.get("configuration").toString(), Object.class);
            perform(put(API + "/settings/" + group), map("expectedRevision", before.get("revision").asLong(), "configuration", c))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(before.get("revision").asLong() + 1))
                    .andExpect(jsonPath("$.data.updatedBy").isNotEmpty());
            perform(get(API + "/settings/" + group + "/history"), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].entityType").value("SETTINGS_" + group.toUpperCase(Locale.ROOT)))
                    .andExpect(jsonPath("$.data.content[0].newData").isNotEmpty());
        }
    }

    @Test
    void invalidAndUnknownValuesLeaveSettingsAndAuditUntouched() throws Exception {
        var before = data(perform(get(API + "/settings/general"), null));
        var c = config("general"); c.put("timezone", "Not/AZone");
        update("general", c).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
        c.put("timezone", "UTC"); c.put("maintenanceMode", true);
        update("general", c).andExpect(status().isBadRequest());
        c.put("maintenanceMode", false); c.put("smtpPassword", "not-accepted");
        update("general", c).andExpect(status().isBadRequest());
        var store = config("store"); store.put("supportEmail", "invalid-email"); update("store", store).andExpect(status().isBadRequest());
        var orders = config("order"); orders.put("maxItems", 101); update("order", orders).andExpect(status().isBadRequest());
        assertThat(data(perform(get(API + "/settings/general"), null))).isEqualTo(before);
        perform(get(API + "/settings/general/history"), null).andExpect(jsonPath("$.data.totalElements").value(0));
        perform(get(API + "/settings/store/history").param("size", "0"), null).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentUpdatesAcceptOneRevisionAndCreateOneAudit() throws Exception {
        var before = data(perform(get(API + "/settings/store"), null));
        var c = config("store"); c.put("storeName", "New " + tag);
        var request = map("expectedRevision", before.get("revision").asLong(), "configuration", c);
        ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> attempt = () -> { start.await(); return perform(put(API + "/settings/store"), request).andReturn().getResponse().getStatus(); };
            var one = executor.submit(attempt); var two = executor.submit(attempt); start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        } finally { executor.shutdownNow(); }
        perform(get(API + "/settings/store/history"), null).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void paymentMethodValidationVersioningAndArchivePreservePaymentHistory() throws Exception {
        var r = map("code", "PAY-" + tag.toUpperCase(Locale.ROOT), "name", "Bank " + tag, "enabled", true,
                "configuration", map("kind", "BANK_TRANSFER"));
        create("/settings/payment-methods", r).andExpect(status().isBadRequest());
        r.put("configuration", map("kind", "BANK_TRANSFER", "bankDetails", map("bankName", "Test Bank", "accountNumber", "123456", "accountHolder", "LemonadeX"), "instructions", "Order code"));
        UUID method = id(create("/settings/payment-methods", r).andExpect(status().isCreated()));
        create("/settings/payment-methods", r).andExpect(status().isConflict());
        UUID order = id(create("/orders", order(1))); UUID payment = id(create("/payments", map("orderId", order, "paymentMethod", r.get("code"), "amount", 100)));
        perform(delete(API + "/settings/payment-methods/" + method).param("expectedRevision", "1"), null).andExpect(status().isConflict());
        perform(delete(API + "/settings/payment-methods/" + method).param("expectedRevision", "0"), null).andExpect(status().isOk());
        perform(get(API + "/settings/payment-methods/" + method), null).andExpect(status().isNotFound());
        create("/settings/payment-methods", r).andExpect(status().isConflict());
        create("/payments", map("orderId", order, "paymentMethod", r.get("code"), "amount", 1)).andExpect(status().isBadRequest());
        perform(put(API + "/payments/" + payment + "/status"), map("status", "PAID")).andExpect(status().isOk());
        perform(get(API + "/settings/payment/history"), null).andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void paymentMethodUpdatesRejectStaleRevisionsAndUnsupportedGateways() throws Exception {
        var r = map("code", "CASH-" + tag.toUpperCase(Locale.ROOT), "name", "Cash", "enabled", true, "configuration", map("kind", "COD"));
        UUID method = id(create("/settings/payment-methods", r));
        var change = map("expectedRevision", 0, "name", "Cash changed", "enabled", false, "configuration", map("kind", "COD"));
        perform(put(API + "/settings/payment-methods/" + method), change).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(1));
        perform(put(API + "/settings/payment-methods/" + method), change).andExpect(status().isConflict());
        r.put("configuration", map("kind", "STRIPE", "apiKey", "unsupported")); create("/settings/payment-methods", r).andExpect(status().isBadRequest());
    }

    @Test
    void configuredShippingFeesAreCalculatedAndSnapshottedBeforeMethodsChange() throws Exception {
        UUID method = shippingMethod(25);
        change("shipping", "useConfiguredFees", true, "freeShippingThreshold", 200).andExpect(status().isOk());
        perform(get(API + "/settings/shipping/quote").param("methodCode", "SHIP-" + tag.toUpperCase(Locale.ROOT)).param("merchandiseAmount", "100"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shippingFee").value(25));
        var request = order(1); request.put("shippingFee", 999); request.put("shippingMethodCode", "ship-" + tag);
        UUID order = id(create("/orders", request).andExpect(jsonPath("$.data.shippingFee").value(25)).andExpect(jsonPath("$.data.totalAmount").value(125)));
        changeOrder(order, "CONFIRMED").andExpect(status().isOk());
        perform(put(API + "/settings/shipping-methods/" + method), map("expectedRevision", 0, "name", "Carrier changed", "provider", "Test", "baseFee", 50, "estimatedDays", 2, "enabled", false)).andExpect(status().isOk());
        perform(get(API + "/orders/" + order), null).andExpect(jsonPath("$.data.shippingFee").value(25));
        create("/orders", request).andExpect(status().isBadRequest());
        change("shipping", "useConfiguredFees", false).andExpect(status().isOk());
        create("/shipments", map("orderId", order, "shippingProvider", "Test", "shippingFee", 50)).andExpect(status().isBadRequest());
        create("/shipments", map("orderId", order, "shippingProvider", "Test", "shippingFee", 25)).andExpect(status().isCreated());
        perform(delete(API + "/settings/shipping-methods/" + method).param("expectedRevision", "1"), null).andExpect(status().isOk());
        perform(get(API + "/orders/" + order), null).andExpect(jsonPath("$.data.shippingMethodCode").value("SHIP-" + tag.toUpperCase(Locale.ROOT)));
    }

    @Test
    void freeShippingUsesNetMerchandiseAndDefaultFeeWhenNoMethodChosen() throws Exception {
        change("shipping", "useConfiguredFees", true, "defaultBaseFee", 20, "freeShippingThreshold", 100).andExpect(status().isOk());
        create("/orders", order(1)).andExpect(jsonPath("$.data.shippingFee").value(0));
        var request = order(1); request.put("discountAmount", 10);
        create("/orders", request).andExpect(jsonPath("$.data.shippingFee").value(20)).andExpect(jsonPath("$.data.totalAmount").value(110));
        perform(get(API + "/settings/shipping/quote").param("merchandiseAmount", "-1"), null).andExpect(status().isBadRequest());
        perform(get(API + "/settings/shipping/quote").param("merchandiseAmount", "1").param("methodCode", "MISSING"), null).andExpect(status().isBadRequest());
    }

    @Test
    void orderLimitsPrefixAndAutomaticConfirmationApplyToNewOrders() throws Exception {
        change("order", "orderCodePrefix", "SHOP", "autoConfirm", true, "maxQuantityPerItem", 2, "minimumOrderAmount", 150).andExpect(status().isOk());
        create("/orders", order(3)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ORDER_ITEM_LIMIT"));
        create("/orders", order(1)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MINIMUM_ORDER_AMOUNT"));
        UUID order = id(create("/orders", order(2)).andExpect(jsonPath("$.data.orderCode").value(startsWith("SHOP-")))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED")).andExpect(jsonPath("$.data.confirmedAt").isNotEmpty()));
        perform(get(API + "/orders/" + order + "/history"), null).andExpect(jsonPath("$.data.totalElements").value(2));
        assertThat(jdbc.queryForObject("select quantity_reserved from inventories where id = ?", Integer.class, stock)).isEqualTo(2);
    }

    @Test
    void maintenanceAndOrderSwitchRejectNewOrdersWithoutDisablingAdministration() throws Exception {
        change("general", "maintenanceMode", true, "maintenanceMessage", "Scheduled maintenance").andExpect(status().isOk());
        create("/orders", order(1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_MAINTENANCE"));
        perform(get(API + "/settings/general"), null).andExpect(status().isOk());
        mvc.perform(get("/api/v1/store/configuration")).andExpect(status().isOk()).andExpect(jsonPath("$.data.ordersEnabled").value(false));
        change("general", "maintenanceMode", false).andExpect(status().isOk());
        change("order", "ordersEnabled", false).andExpect(status().isOk());
        create("/orders", order(1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDERS_DISABLED"));
        assertThat(jdbc.queryForObject("select quantity_reserved from inventories where id = ?", Integer.class, stock)).isZero();
    }

    @Test
    void paymentFlagsControlCreationAndKeepPendingPaymentsSettleable() throws Exception {
        UUID order = id(create("/orders", order(1)));
        change("payment", "minimumPaymentAmount", 10, "allowPartialPayments", false).andExpect(status().isOk());
        create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 5)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MINIMUM_PAYMENT_AMOUNT"));
        create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 50)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARTIAL_PAYMENT_DISABLED"));
        UUID payment = id(create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 100)));
        change("payment", "paymentsEnabled", false).andExpect(status().isOk());
        create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 100)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENTS_DISABLED"));
        perform(put(API + "/payments/" + payment + "/status"), map("status", "PAID")).andExpect(status().isOk());
    }

    @Test
    void disabledShippingPreservesDeliveryOfExistingShipments() throws Exception {
        UUID order = id(create("/orders", order(1))); UUID shipment = dispatch(order);
        change("shipping", "shippingEnabled", false).andExpect(status().isOk());
        changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        create("/orders", order(1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHIPPING_DISABLED"));
        create("/shipments", map("orderId", order, "shippingProvider", "Test", "shippingFee", 0)).andExpect(status().isConflict());
    }

    @Test
    void cancellationAndReturnSettingsApplyWithoutChangingExistingStock() throws Exception {
        UUID order = id(create("/orders", order(1)));
        change("order", "allowCancellation", false).andExpect(status().isOk());
        changeOrder(order, "CANCELLED").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCELLATION_DISABLED"));
        assertThat(jdbc.queryForObject("select quantity_reserved from inventories where id = ?", Integer.class, stock)).isEqualTo(1);
        UUID shipment = dispatch(order); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        var r = returned(order, item(order), 1, "RETURN", null);
        change("order", "allowReturns", false).andExpect(status().isOk());
        create("/returns", r).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETURNS_DISABLED"));
        change("order", "allowReturns", true, "returnWindowDays", 1).andExpect(status().isOk());
        jdbc.update("update shipments set shipped_at = shipped_at - interval '2 days', delivered_at = delivered_at - interval '2 days' where id = ?", shipment);
        create("/returns", r).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETURN_WINDOW_EXPIRED"));
        change("order", "returnWindowDays", null).andExpect(status().isOk());
        create("/returns", r).andExpect(status().isCreated());
    }

    @Test
    void notificationFlagsAndRecipientLimitsRollBackPublicationAtomically() throws Exception {
        UUID notice = id(create("/notifications", map("title", "Notification " + tag, "content", "Announcement", "notificationType", "PROMOTION", "targetType", "ALL", "customerIds", List.of())));
        change("notification", "inAppEnabled", false).andExpect(status().isOk());
        perform(post(API + "/notifications/" + notice + "/publish"), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTIFICATIONS_DISABLED"));
        change("notification", "inAppEnabled", true, "allowBroadcast", false).andExpect(status().isOk());
        perform(post(API + "/notifications/" + notice + "/publish"), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BROADCAST_DISABLED"));
        change("notification", "allowBroadcast", true, "maxRecipients", 1).andExpect(status().isOk());
        perform(post(API + "/notifications/" + notice + "/publish"), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTIFICATION_RECIPIENT_LIMIT"));
        perform(get(API + "/notifications/" + notice), null).andExpect(jsonPath("$.data.status").value("DRAFT")).andExpect(jsonPath("$.data.recipientCount").value(0));
        change("notification", "maxRecipients", 1000000).andExpect(status().isOk());
        perform(post(API + "/notifications/" + notice + "/publish"), null).andExpect(status().isOk());
        change("notification", "inAppEnabled", false).andExpect(status().isOk());
        perform(post(API + "/notifications/" + notice + "/publish"), null).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)).andExpect(status().isOk());
    }

    @Test
    void generalTimezoneBecomesReportDefaultAndCanBeOverridden() throws Exception {
        change("general", "timezone", "UTC", "language", "EN").andExpect(status().isOk());
        perform(get(API + "/reports/orders"), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.period.timezone").value("UTC"));
        perform(get(API + "/reports/orders").param("timezone", "Asia/Ho_Chi_Minh"), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.period.timezone").value("Asia/Ho_Chi_Minh"));
    }

    @Test
    void publicConfigurationExposesEnabledOptionsWithoutInternalBankOrAuditData() throws Exception {
        shippingMethod(25);
        var config = mvc.perform(get("/api/v1/store/configuration")).andExpect(status().isOk()).andExpect(jsonPath("$.data.store.storeName").value("LemonadeX"))
                .andExpect(jsonPath("$.data.paymentMethods[0].configuration").doesNotExist()).andExpect(jsonPath("$.data.updatedBy").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertThat(config).doesNotContain("accountNumber", "accountHolder", "expectedRevision", "oldData");
        change("payment", "paymentsEnabled", false).andExpect(status().isOk()); change("shipping", "shippingEnabled", false).andExpect(status().isOk());
        mvc.perform(get("/api/v1/store/configuration")).andExpect(jsonPath("$.data.paymentMethods").isEmpty()).andExpect(jsonPath("$.data.shippingMethods").isEmpty());
    }

    @Test
    void eachGroupRequiresItsOwnReadAndWritePermission() throws Exception {
        UUID role = jdbc.queryForObject("select id from roles where code = 'ADMIN'", UUID.class);
        for (String group : List.of("store", "payment", "shipping", "order", "notification", "general")) {
            var c = config(group); long revision = data(perform(get(API + "/settings/" + group), null)).get("revision").asLong();
            for (String action : List.of("WRITE", "READ")) {
                UUID permission = jdbc.queryForObject("select id from permissions where code = ?", UUID.class, "SETTINGS_" + group.toUpperCase(Locale.ROOT) + "_" + action);
                jdbc.update("delete from role_permissions where role_id = ? and permission_id = ?", role, permission);
                try {
                    if (action.equals("WRITE")) {
                        perform(put(API + "/settings/" + group), map("expectedRevision", revision, "configuration", c)).andExpect(status().isForbidden());
                        perform(get(API + "/settings/" + group), null).andExpect(status().isOk());
                    } else {
                        perform(get(API + "/settings/" + group), null).andExpect(status().isForbidden());
                        perform(get(API + "/settings/" + group + "/history"), null).andExpect(status().isForbidden());
                    }
                } finally { jdbc.update("insert into role_permissions(role_id, permission_id) values (?, ?) on conflict do nothing", role, permission); }
            }
        }
        mvc.perform(get(API + "/settings/store")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/settings/store").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)).andExpect(status().isForbidden());
    }

    @Test
    void marketingDefaultCanBeOverriddenOnEachOrder() throws Exception {
        UUID sale = id(create("/flash-sales", map("name", "Flash " + tag, "startAt", Instant.now().minusSeconds(60).toString(), "endAt", Instant.now().plusSeconds(3600).toString(), "status", "ACTIVE",
                "items", List.of(map("productVariantId", variant, "flashPrice", 50, "quantityLimit", 5)))));
        change("order", "enableMarketingByDefault", true).andExpect(status().isOk());
        create("/orders", order(1)).andExpect(jsonPath("$.data.totalAmount").value(50));
        var request = order(1); request.put("applyMarketing", false); create("/orders", request).andExpect(jsonPath("$.data.totalAmount").value(100));
        assertThat(jdbc.queryForObject("select sold_quantity from flash_sale_items where flash_sale_id = ?", Integer.class, sale)).isEqualTo(1);
    }

    @Test
    void carrierReturnsRemainRefundableWhenCustomerReturnsAreDisabled() throws Exception {
        UUID order = id(create("/orders", order(1))); UUID shipment = dispatch(order);
        change("order", "allowReturns", false, "returnWindowDays", 1).andExpect(status().isOk());
        changeShipment(shipment, "RETURNED").andExpect(status().isOk());
        create("/returns", returned(order, item(order), 1, "RETURN", null)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select quantity_on_hand from inventories where id = ?", Integer.class, stock)).isEqualTo(10);
    }

    private JsonNode data(ResultActions r) throws Exception { return body(r).get("data"); }
    @SuppressWarnings("unchecked")
    private Map<String, Object> config(String group) throws Exception { return json.readValue(data(perform(get(API + "/settings/" + group), null)).get("configuration").toString(), Map.class); }
    private ResultActions update(String group, Map<String, Object> config) throws Exception {
        long revision = data(perform(get(API + "/settings/" + group), null)).get("revision").asLong();
        return perform(put(API + "/settings/" + group), map("expectedRevision", revision, "configuration", config));
    }
    private ResultActions change(String group, Object... fields) throws Exception { var c = config(group); c.putAll(map(fields)); return update(group, c); }
    private UUID shippingMethod(int fee) throws Exception { return id(create("/settings/shipping-methods", map("code", "SHIP-" + tag.toUpperCase(Locale.ROOT), "name", "Shipping " + tag, "provider", "Test", "baseFee", fee, "estimatedDays", 2, "enabled", true))); }

    private Map<String, Object> order(int quantity) {
        return map("customerId", customer, "warehouseId", warehouse, "recipientName", "Nguyễn An", "recipientPhone", "0901234567",
                "shippingAddress", "Hồ Chí Minh", "discountAmount", 0, "shippingFee", 0,
                "items", List.of(map("productVariantId", variant, "quantity", quantity, "discountAmount", 0)));
    }
    private Map<String, Object> returned(UUID order, UUID item, int quantity, String type, UUID replacement) {
        return map("orderId", order, "requestType", type, "reason", "Đổi size hoặc trả hàng", "images", List.of("https://img.example.com/return.png"),
                "items", List.of(map("orderItemId", item, "quantity", quantity, "replacementVariantId", replacement)));
    }
    private Map<String, Object> refund(UUID returned, UUID payment, int amount) { return map("returnRequestId", returned, "paymentId", payment, "amount", amount, "refundMethod", "BANK_TRANSFER", "reason", "Trả hàng"); }
    private UUID dispatch(UUID order) throws Exception {
        changeOrder(order, "CONFIRMED").andExpect(status().isOk());
        UUID shipment = id(create("/shipments", map("orderId", order, "shippingProvider", "Local " + tag, "trackingCode", UUID.randomUUID().toString(), "shippingFee", 0)));
        changeShipment(shipment, "SHIPPED").andExpect(status().isOk()); return shipment;
    }
    private UUID item(UUID order) throws Exception { return UUID.fromString(body(perform(get(API + "/orders/" + order), null).andExpect(status().isOk())).get("data").get("items").get(0).get("id").asString()); }
    private ResultActions changeOrder(UUID id, String status) throws Exception { return perform(put(API + "/orders/" + id + "/status"), map("status", status)); }
    private ResultActions changeShipment(UUID id, String status) throws Exception { return perform(put(API + "/shipments/" + id + "/status"), map("status", status)); }
    private ResultActions changeReturn(UUID id, String status) throws Exception { return perform(put(API + "/returns/" + id + "/status"), map("status", status)); }
    private ResultActions movement(UUID variant, String type, int quantity) throws Exception { return create("/inventory/transactions", map("warehouseId", warehouse, "productVariantId", variant, "transactionType", type, "quantity", quantity, "note", "Movement " + tag)); }
    private void assertStock(UUID id, int onHand, int reserved) throws Exception {
        perform(get(API + "/inventory/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.quantityOnHand").value(onHand))
                .andExpect(jsonPath("$.data.quantityReserved").value(reserved)).andExpect(jsonPath("$.data.quantityAvailable").value(onHand - reserved));
    }
    private ResultActions create(String path, Map<String, ?> request) throws Exception { return perform(post(API + path), request); }
    private UUID id(ResultActions result) throws Exception { return UUID.fromString(body(result.andExpect(status().isCreated())).get("data").get("id").asString()); }
    private JsonNode body(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private ResultActions perform(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }
}
