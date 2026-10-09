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
class ContentReportIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    private static final String API = "/api/v1/admin";
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private String token;
    private UUID customer, warehouse, product, variant, replacement, stock, replacementStock;

    @BeforeEach
    void fixtures() throws Exception {
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
        // Remove only this test's fixtures, in foreign-key order; API records themselves remain durable.
        String orders = "select id from orders where customer_id in (select id from customers where full_name like ?)";
        String returned = "select id from return_requests where order_id in (" + orders + ")";
        String payments = "select id from payments where order_id in (" + orders + ")";
        String shipments = "select id from shipments where order_id in (" + orders + ")";
        String like = "%" + tag + "%";
        jdbc.update("delete from articles where title like ?", like);
        jdbc.update("delete from store_policies where title like ?", like);
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
        if (customerAccount != null) jdbc.update("delete from accounts where id = ?", customerAccount);
        if (otherAccount != null) jdbc.update("delete from accounts where id = ?", otherAccount);
    }

    @Test
    void articleLifecyclePublishesOnlyVisibleContentAndKeepsImageEvidence() throws Exception {
        Map<String, Object> request = article(); UUID id = id(create("/articles", request));
        String slug = body(perform(get(API + "/articles/" + id), null)).get("data").get("slug").asString();
        mvc.perform(get("/api/v1/content/articles/" + slug)).andExpect(status().isNotFound());
        perform(put(API + "/articles/" + id + "/status"), map("status", "PUBLISHED")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/content/articles/" + slug)).andExpect(status().isOk()).andExpect(jsonPath("$.data.images[0]").value("https://example.com/lookbook.jpg"))
                .andExpect(jsonPath("$.data.authorId").doesNotExist());
        mvc.perform(get("/api/v1/content/articles").param("search", tag).param("articleType", "LOOKBOOK")).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].content").doesNotExist());
        perform(put(API + "/articles/" + id), request).andExpect(status().isConflict());
        perform(put(API + "/articles/" + id + "/status"), map("status", "DRAFT")).andExpect(status().isOk());
        perform(put(API + "/articles/" + id), request).andExpect(status().isOk());
        perform(delete(API + "/articles/" + id), null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from article_images where article_id = ?", Integer.class, id)).isEqualTo(1);
        create("/articles", request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_SLUG_EXISTS"));
        perform(get(API + "/articles/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void articleValidationRejectsInvalidImagesSlugsAndEmptyPublishedLookbooks() throws Exception {
        Map<String, Object> r = article(); r.put("images", List.of()); UUID id = id(create("/articles", r));
        perform(put(API + "/articles/" + id + "/status"), map("status", "PUBLISHED")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOOKBOOK_REQUIRES_IMAGES"));
        r.put("images", List.of("javascript:alert(1)")); perform(put(API + "/articles/" + id), r).andExpect(status().isBadRequest());
        r.put("images", List.of("https://example.com/x.png", "https://example.com/x.png")); perform(put(API + "/articles/" + id), r).andExpect(status().isBadRequest());
        r.put("images", List.of()); r.put("slug", "INVALID SLUG"); perform(put(API + "/articles/" + id), r).andExpect(status().isBadRequest());
        perform(get(API + "/articles").param("search", "%"), null).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void policyActivationArchivesPreviousVersionAndProtectsPublishedHistory() throws Exception {
        Map<String, Object> r = policy(); UUID first = id(create("/store-policies", r).andExpect(jsonPath("$.data.version").value(1)));
        mvc.perform(get("/api/v1/content/store-policies/WARRANTY")).andExpect(status().isNotFound());
        perform(put(API + "/store-policies/" + first + "/status"), map("status", "ACTIVE")).andExpect(status().isOk());
        perform(put(API + "/store-policies/" + first), r).andExpect(status().isConflict());
        perform(delete(API + "/store-policies/" + first), null).andExpect(status().isConflict());
        UUID second = id(create("/store-policies", r).andExpect(jsonPath("$.data.version").value(2)));
        perform(put(API + "/store-policies/" + second + "/status"), map("status", "ACTIVE")).andExpect(status().isOk());
        perform(get(API + "/store-policies/" + first), null).andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mvc.perform(get("/api/v1/content/store-policies/WARRANTY")).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2)).andExpect(jsonPath("$.data.updatedBy").doesNotExist());
        perform(put(API + "/store-policies/" + first + "/status"), map("status", "ACTIVE")).andExpect(status().isConflict());
        UUID draft = id(create("/store-policies", r)); perform(delete(API + "/store-policies/" + draft), null).andExpect(status().isOk());
        create("/store-policies", r).andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(4));
    }

    @Test
    void concurrentPolicyVersionsAndActivationKeepOneActiveVersion() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<UUID> attempt = () -> { start.await(); return id(create("/store-policies", policy())); };
            Future<UUID> one = executor.submit(attempt), two = executor.submit(attempt); start.countDown();
            UUID first = one.get(20, TimeUnit.SECONDS), second = two.get(20, TimeUnit.SECONDS);
            assertThat(jdbc.queryForList("select version from store_policies where title like ? order by version", Integer.class, "%" + tag + "%")).containsExactly(1,2);
            Future<Integer> a = executor.submit(() -> perform(put(API + "/store-policies/" + first + "/status"), map("status", "ACTIVE")).andReturn().getResponse().getStatus());
            Future<Integer> b = executor.submit(() -> perform(put(API + "/store-policies/" + second + "/status"), map("status", "ACTIVE")).andReturn().getResponse().getStatus());
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(200); assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(jdbc.queryForObject("select count(*) from store_policies where policy_type = 'WARRANTY' and status = 'ACTIVE' and not deleted", Integer.class)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void emptyReportsReturnZeroesAndFillDayWeekMonthBuckets() throws Exception {
        for (String path : List.of("revenue", "orders", "bestsellers", "customers", "returns", "promotions"))
            perform(get(API + "/reports/" + path).param("from", "2025-01-01T00:00:00Z").param("to", "2025-01-03T00:00:00Z").param("timezone", "UTC").param("warehouseId", warehouse.toString()), null).andExpect(status().isOk());
        perform(get(API + "/reports/revenue").param("from", "2025-01-01T00:00:00Z").param("to", "2025-01-03T00:00:00Z").param("timezone", "UTC"), null)
                .andExpect(jsonPath("$.data.paidAmount").value(0)).andExpect(jsonPath("$.data.series.length()").value(2));
        perform(get(API + "/reports/orders").param("from", "2025-01-01T00:00:00Z").param("to", "2025-02-02T00:00:00Z").param("timezone", "UTC").param("groupBy", "MONTH"), null)
                .andExpect(jsonPath("$.data.series.length()").value(2)).andExpect(jsonPath("$.data.series[1].bucketStart").value("2025-02-01"));
        perform(get(API + "/reports/revenue").param("from", "2025-01-01T00:00:00Z").param("to", "2025-01-03T00:00:00Z").param("timezone", "UTC").param("groupBy", "WEEK"), null)
                .andExpect(jsonPath("$.data.series[0].bucketStart").value("2024-12-30"));
    }

    @Test
    void cashRevenueUsesEventDatesAndDoesNotMultiplyPaymentsOrRefunds() throws Exception {
        UUID order = id(create("/orders", order(2)));
        UUID p1 = id(create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 100)));
        UUID p2 = id(create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 100)));
        perform(put(API + "/payments/" + p1 + "/status"), map("status", "PAID")).andExpect(status().isOk());
        perform(put(API + "/payments/" + p2 + "/status"), map("status", "PAID")).andExpect(status().isOk());
        UUID shipment = dispatch(order); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        UUID returned = id(create("/returns", returned(order, item(order), 1, "RETURN", null))); changeReturn(returned, "APPROVED"); changeReturn(returned, "COMPLETED");
        UUID f1 = id(create("/refunds", refund(returned, p1, 40))), f2 = id(create("/refunds", refund(returned, p2, 45)));
        perform(put(API + "/refunds/" + f1 + "/status"), map("status", "SUCCEEDED")).andExpect(status().isOk());
        perform(put(API + "/refunds/" + f2 + "/status"), map("status", "SUCCEEDED")).andExpect(status().isOk());
        jdbc.update("update payments set paid_at = '2025-12-31T17:30:00Z' where order_id = ?", order);
        jdbc.update("update refunds set processed_at = '2025-12-31T16:30:00Z' where return_request_id = ?", returned);
        perform(get(API + "/reports/revenue").param("from", "2025-12-31T00:00:00Z").param("to", "2026-01-02T00:00:00Z").param("warehouseId", warehouse.toString()), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paidAmount").value(200)).andExpect(jsonPath("$.data.refundedAmount").value(85))
                .andExpect(jsonPath("$.data.netReceivedAmount").value(115)).andExpect(jsonPath("$.data.series[0].netReceivedAmount").value(-85))
                .andExpect(jsonPath("$.data.series[1].bucketStart").value("2026-01-01")).andExpect(jsonPath("$.data.series[1].paidAmount").value(200));
        perform(get(API + "/reports/revenue").param("from", "2025-12-31T16:30:00Z").param("to", "2025-12-31T17:30:00Z").param("warehouseId", warehouse.toString()), null)
                .andExpect(jsonPath("$.data.paidAmount").value(0)).andExpect(jsonPath("$.data.refundedAmount").value(85));
    }

    @Test
    void productCustomerOrderAndReturnReportsUseDiscountedRetainedPurchases() throws Exception {
        Map<String, Object> r = order(2); r.put("items", List.of(map("productVariantId", variant, "quantity", 2, "discountAmount", 20))); r.put("discountAmount", 10);
        UUID first = id(create("/orders", r)); UUID payment = id(create("/payments", map("orderId", first, "paymentMethod", "COD", "amount", 170)));
        perform(put(API + "/payments/" + payment + "/status"), map("status", "PAID")).andExpect(status().isOk());
        UUID shipment = dispatch(first); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        UUID returned = id(create("/returns", returned(first, item(first), 1, "RETURN", null))); changeReturn(returned, "APPROVED"); changeReturn(returned, "COMPLETED");
        UUID f1 = id(create("/refunds", refund(returned, payment, 40))), f2 = id(create("/refunds", refund(returned, payment, 40)));
        perform(put(API + "/refunds/" + f1 + "/status"), map("status", "SUCCEEDED")).andExpect(status().isOk());
        perform(put(API + "/refunds/" + f2 + "/status"), map("status", "SUCCEEDED")).andExpect(status().isOk());
        UUID second = id(create("/orders", order(1))); shipment = dispatch(second); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        create("/orders", order(1)).andExpect(status().isCreated()); UUID cancelled = id(create("/orders", order(1))); changeOrder(cancelled, "CANCELLED").andExpect(status().isOk());
        perform(get(API + "/reports/bestsellers").param("warehouseId", warehouse.toString()), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.products.totalElements").value(1))
                .andExpect(jsonPath("$.data.products.content[0].grossQuantity").value(3)).andExpect(jsonPath("$.data.products.content[0].returnedQuantity").value(1))
                .andExpect(jsonPath("$.data.products.content[0].netQuantity").value(2)).andExpect(jsonPath("$.data.products.content[0].merchandiseAmount").value(185));
        perform(get(API + "/reports/customers").param("warehouseId", warehouse.toString()), null).andExpect(jsonPath("$.data.purchasingCustomers").value(1)).andExpect(jsonPath("$.data.repeatCustomers").value(1))
                .andExpect(jsonPath("$.data.customers.content[0].orderAmount").value(270)).andExpect(jsonPath("$.data.customers.content[0].refundedAmount").value(80)).andExpect(jsonPath("$.data.customers.content[0].netOrderAmount").value(190));
        perform(get(API + "/reports/orders").param("warehouseId", warehouse.toString()), null).andExpect(jsonPath("$.data.totalOrders").value(4)).andExpect(jsonPath("$.data.cancelledOrders").value(1))
                .andExpect(jsonPath("$.data.orderAmount").value(370)).andExpect(jsonPath("$.data.cancelledAmount").value(100)).andExpect(jsonPath("$.data.averageOrderValue").value(123.33));
        perform(get(API + "/reports/returns").param("warehouseId", warehouse.toString()), null).andExpect(jsonPath("$.data.totalRequests").value(1)).andExpect(jsonPath("$.data.completedQuantity").value(1))
                .andExpect(jsonPath("$.data.refundedAmount").value(80)).andExpect(jsonPath("$.data.requests.content[0].refundedAmount").value(80));
        perform(delete(API + "/products/" + product), null).andExpect(status().isOk());
        perform(get(API + "/reports/bestsellers").param("warehouseId", warehouse.toString()), null).andExpect(jsonPath("$.data.products.content[0].deleted").value(true));
        perform(get(API + "/reports/bestsellers").param("warehouseId", warehouse.toString()).param("page", "9").param("size", "1"), null).andExpect(jsonPath("$.data.products.totalElements").value(1)).andExpect(jsonPath("$.data.products.content.length()").value(0));
    }

    @Test
    void inventoryReportsKeepReservationsRetailValueAndRetiredProducts() throws Exception {
        id(create("/orders", order(2)));
        perform(get(API + "/reports/inventory").param("warehouseId", warehouse.toString()), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.stockRecords").value(2))
                .andExpect(jsonPath("$.data.quantityOnHand").value(20)).andExpect(jsonPath("$.data.quantityReserved").value(2)).andExpect(jsonPath("$.data.quantityAvailable").value(18)).andExpect(jsonPath("$.data.retailValue").value(2000));
        perform(get(API + "/reports/inventory").param("warehouseId", warehouse.toString()).param("lowStock", "true").param("threshold", "8"), null).andExpect(jsonPath("$.data.stocks.totalElements").value(1));
        perform(get(API + "/reports/inventory").param("warehouseId", warehouse.toString()).param("search", "%"), null).andExpect(jsonPath("$.data.stockRecords").value(0));
        perform(delete(API + "/products/" + product), null).andExpect(status().isOk());
        perform(get(API + "/reports/inventory").param("warehouseId", warehouse.toString()).param("page", "5").param("size", "1"), null).andExpect(jsonPath("$.data.stocks.totalElements").value(2)).andExpect(jsonPath("$.data.quantityOnHand").value(20));
    }

    @Test
    void campaignReportsDeduplicateOrdersAndRetainReleasedArchivedAllocations() throws Exception {
        Map<String, Object> c = coupon(10, "FIXED_AMOUNT"); c.put("usageLimitPerCustomer", 10); UUID coupon = id(create("/coupons", c));
        UUID flash = id(create("/flash-sales", flash(50, 8)));
        Map<String, Object> r = order(2); r.put("applyMarketing", true); r.put("couponCode", "save-" + tag); id(create("/orders", r));
        r = order(1); r.put("applyMarketing", true); r.put("couponCode", "save-" + tag); UUID cancelled = id(create("/orders", r)); changeOrder(cancelled, "CANCELLED").andExpect(status().isOk());
        perform(delete(API + "/flash-sales/" + flash), null).andExpect(status().isOk());
        perform(delete(API + "/coupons/" + coupon), null).andExpect(status().isOk());
        perform(get(API + "/reports/promotions").param("warehouseId", warehouse.toString()).param("campaignType", "COUPON"), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.campaigns.totalElements").value(1))
                .andExpect(jsonPath("$.data.campaigns.content[0].orderCount").value(1)).andExpect(jsonPath("$.data.campaigns.content[0].releasedOrderCount").value(1))
                .andExpect(jsonPath("$.data.campaigns.content[0].discountAmount").value(10)).andExpect(jsonPath("$.data.campaigns.content[0].attributedOrderAmount").value(90)).andExpect(jsonPath("$.data.campaigns.content[0].deleted").value(true));
        perform(get(API + "/reports/promotions").param("warehouseId", warehouse.toString()).param("campaignType", "FLASH_SALE"), null).andExpect(jsonPath("$.data.campaigns.content[0].quantity").value(2))
                .andExpect(jsonPath("$.data.campaigns.content[0].discountAmount").value(100)).andExpect(jsonPath("$.data.campaigns.content[0].attributedOrderAmount").value(90));
        perform(get(API + "/reports/promotions").param("warehouseId", warehouse.toString()).param("page", "9").param("size", "1"), null).andExpect(jsonPath("$.data.campaigns.totalElements").value(2)).andExpect(jsonPath("$.data.campaigns.content.length()").value(0));
    }

    @Test
    void reportsRejectInvalidDatesTimezonesEnumsAndPaging() throws Exception {
        perform(get(API + "/reports/revenue").param("from", "2026-01-02T00:00:00Z").param("to", "2026-01-01T00:00:00Z"), null).andExpect(status().isBadRequest());
        perform(get(API + "/reports/revenue").param("from", "2020-01-01T00:00:00Z"), null).andExpect(status().isBadRequest());
        perform(get(API + "/reports/revenue").param("timezone", "not-a-zone"), null).andExpect(status().isBadRequest());
        perform(get(API + "/reports/revenue").param("groupBy", "YEAR"), null).andExpect(status().isBadRequest());
        perform(get(API + "/reports/bestsellers").param("size", "51"), null).andExpect(status().isBadRequest());
        perform(get(API + "/reports/inventory").param("threshold", "-1"), null).andExpect(status().isBadRequest());
    }

    @Test
    void contentAndEveryReportHaveIndependentReadPermissions() throws Exception {
        Map<String, String> paths = Map.ofEntries(Map.entry("ARTICLE_READ", "/articles"), Map.entry("POLICY_READ", "/store-policies"),
                Map.entry("REPORT_REVENUE_READ", "/reports/revenue"), Map.entry("REPORT_ORDER_READ", "/reports/orders"), Map.entry("REPORT_PRODUCT_READ", "/reports/bestsellers"),
                Map.entry("REPORT_INVENTORY_READ", "/reports/inventory"), Map.entry("REPORT_CUSTOMER_READ", "/reports/customers"), Map.entry("REPORT_RETURN_READ", "/reports/returns"), Map.entry("REPORT_PROMOTION_READ", "/reports/promotions"));
        for (var entry : paths.entrySet()) {
            mvc.perform(get(API + entry.getValue())).andExpect(status().isUnauthorized());
            asCustomer(get(API + entry.getValue()), null, customerToken).andExpect(status().isForbidden());
            UUID permission = jdbc.queryForObject("select id from permissions where code = ?", UUID.class, entry.getKey()); UUID role = jdbc.queryForObject("select id from roles where code = 'ADMIN'", UUID.class);
            jdbc.update("delete from role_permissions where role_id = ? and permission_id = ?", role, permission);
            try { perform(get(API + entry.getValue()), null).andExpect(status().isForbidden()); }
            finally { jdbc.update("insert into role_permissions(role_id, permission_id) values(?,?) on conflict do nothing", role, permission); }
        }
    }

    private Map<String, Object> article() { return map("title", "Lookbook " + tag, "content", "Summer collection", "articleType", "LOOKBOOK", "images", List.of("https://example.com/lookbook.jpg")); }
    private Map<String, Object> policy() { return map("policyType", "WARRANTY", "title", "Policy " + tag, "content", "Warranty terms"); }

    @Test
    void multipleDiscountedLinesAttributeAnOrderOnlyOnceToEachPromotion() throws Exception {
        UUID promotion = id(create("/promotions", promotion(20))); id(create("/coupons", coupon(10, "FIXED_AMOUNT")));
        Map<String, Object> r = order(2); r.put("items", List.of(map("productVariantId", variant, "quantity", 2, "discountAmount", 0), map("productVariantId", replacement, "quantity", 1, "discountAmount", 0)));
        r.put("discountAmount", 20); r.put("applyMarketing", true); r.put("couponCode", "save-" + tag);
        UUID order = id(create("/orders", r).andExpect(jsonPath("$.data.totalAmount").value(210)));
        UUID shipment = dispatch(order); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        perform(get(API + "/reports/promotions").param("warehouseId", warehouse.toString()).param("campaignType", "PROMOTION"), null)
                .andExpect(jsonPath("$.data.campaigns.content[0].campaignId").value(promotion.toString())).andExpect(jsonPath("$.data.campaigns.content[0].orderCount").value(1))
                .andExpect(jsonPath("$.data.campaigns.content[0].quantity").value(3)).andExpect(jsonPath("$.data.campaigns.content[0].discountAmount").value(60)).andExpect(jsonPath("$.data.campaigns.content[0].attributedOrderAmount").value(210));
        perform(get(API + "/reports/bestsellers").param("warehouseId", warehouse.toString()), null).andExpect(jsonPath("$.data.products.content[0].orderCount").value(1))
                .andExpect(jsonPath("$.data.products.content[0].grossQuantity").value(3)).andExpect(jsonPath("$.data.products.content[0].merchandiseAmount").value(210));
    }

    @Test
    void fullyDiscountedOrdersRemainReportableWithoutDivisionByZero() throws Exception {
        id(create("/promotions", promotion(100))); Map<String, Object> r = order(1); r.put("applyMarketing", true);
        UUID order = id(create("/orders", r).andExpect(jsonPath("$.data.totalAmount").value(0)));
        UUID shipment = dispatch(order); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        perform(get(API + "/reports/bestsellers").param("warehouseId", warehouse.toString()), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.products.content[0].netQuantity").value(1)).andExpect(jsonPath("$.data.products.content[0].merchandiseAmount").value(0));
        perform(get(API + "/reports/customers").param("warehouseId", warehouse.toString()), null).andExpect(jsonPath("$.data.purchasingCustomers").value(1)).andExpect(jsonPath("$.data.customers.content[0].netOrderAmount").value(0));
    }

    @Test
    void contentWritePermissionsAreRequiredAndVietnameseTitlesGenerateSlugs() throws Exception {
        Map<String, Object> article = article(); article.put("title", "Áo Đẹp " + tag);
        create("/articles", article).andExpect(status().isCreated()).andExpect(jsonPath("$.data.slug").value("ao-dep-" + tag));
        UUID role = jdbc.queryForObject("select id from roles where code = 'ADMIN'", UUID.class);
        for (String module : List.of("ARTICLE", "POLICY")) {
            UUID permission = jdbc.queryForObject("select id from permissions where code = ?", UUID.class, module + "_WRITE");
            jdbc.update("delete from role_permissions where role_id = ? and permission_id = ?", role, permission);
            try { create(module.equals("ARTICLE") ? "/articles" : "/store-policies", module.equals("ARTICLE") ? article() : policy()).andExpect(status().isForbidden()); }
            finally { jdbc.update("insert into role_permissions(role_id, permission_id) values(?,?) on conflict do nothing", role, permission); }
        }
    }

    private Map<String, Object> coupon(int value, String type) {
        return map("code", "save-" + tag, "name", "Coupon " + tag, "discountType", type, "discountValue", value, "minimumOrderValue", 0,
                "usageLimit", 10, "usageLimitPerCustomer", 1, "startAt", Instant.now().minusSeconds(60).toString(), "endAt", Instant.now().plusSeconds(3600).toString(), "status", "ACTIVE");
    }
    private Map<String, Object> promotion(int value) {
        return map("name", "Promotion " + tag, "promotionType", "PRODUCT_DISCOUNT", "discountType", "PERCENTAGE", "discountValue", value,
                "priority", 1, "startAt", Instant.now().minusSeconds(60).toString(), "endAt", Instant.now().plusSeconds(3600).toString(), "status", "ACTIVE", "productIds", List.of(product), "categoryIds", List.of());
    }
    private Map<String, Object> flash(int price, int limit) {
        return map("name", "Flash " + tag, "startAt", Instant.now().minusSeconds(60).toString(), "endAt", Instant.now().plusSeconds(3600).toString(), "status", "ACTIVE",
                "items", List.of(map("productVariantId", variant, "flashPrice", price, "quantityLimit", limit)));
    }
    private Map<String, Object> banner() { return map("title", "Banner " + tag, "imageUrl", "https://example.com/banner.png", "linkUrl", "/products", "position", "HOME", "sortOrder", 1, "status", "ACTIVE"); }
    private Map<String, Object> notification(String target, List<UUID> customers) { return map("title", "Notification " + tag, "content", "New collection", "notificationType", "PROMOTION", "targetType", target, "customerIds", customers); }
    private ResultActions asCustomer(MockHttpServletRequestBuilder request, Object body, String token) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)); return mvc.perform(request);
    }
    private void concurrentOrders(Map<String, Object> request) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> attempt = () -> { start.await(); return create("/orders", request).andReturn().getResponse().getStatus(); };
            Future<Integer> one = executor.submit(attempt), two = executor.submit(attempt); start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        } finally { executor.shutdownNow(); }
    }

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
