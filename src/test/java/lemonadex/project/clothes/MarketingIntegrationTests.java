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
class MarketingIntegrationTests extends PostgresTestSupport {
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
    void couponCrudValidationSearchAndArchiveRetainUniqueCodes() throws Exception {
        Map<String, Object> request = coupon(10, "PERCENTAGE");
        UUID coupon = id(create("/coupons", request).andExpect(jsonPath("$.data.code").value("SAVE-" + tag.toUpperCase(Locale.ROOT))));
        perform(get(API + "/coupons").param("search", tag), null).andExpect(jsonPath("$.data.totalElements").value(1));
        perform(get(API + "/coupons").param("search", "%"), null).andExpect(jsonPath("$.data.totalElements").value(0));
        create("/coupons", request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COUPON_CODE_EXISTS"));
        request.put("discountValue", 101);
        perform(put(API + "/coupons/" + coupon), request).andExpect(status().isBadRequest());
        request.put("discountValue", 15); request.put("endAt", request.get("startAt"));
        perform(put(API + "/coupons/" + coupon), request).andExpect(status().isBadRequest());
        perform(delete(API + "/coupons/" + coupon), null).andExpect(status().isOk());
        perform(get(API + "/coupons/" + coupon), null).andExpect(status().isNotFound());
        create("/coupons", coupon(10, "PERCENTAGE")).andExpect(status().isConflict());
    }

    @Test
    void couponCapsMinimumsExpiryAndPerCustomerUsageApplyToNetMerchandise() throws Exception {
        Map<String, Object> c = coupon(50, "PERCENTAGE"); c.put("maxDiscount", 25); c.put("minimumOrderValue", 150);
        UUID coupon = id(create("/coupons", c));
        Map<String, Object> o = order(1); o.put("couponCode", "save-" + tag);
        create("/orders", o).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COUPON_MINIMUM_NOT_MET"));
        o = order(2); o.put("couponCode", "save-" + tag); o.put("shippingFee", 20);
        UUID first = id(create("/orders", o).andExpect(jsonPath("$.data.discountAmount").value(25)).andExpect(jsonPath("$.data.totalAmount").value(195)));
        create("/orders", o).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COUPON_LIMIT_REACHED"));
        perform(get(API + "/coupons/" + coupon), null).andExpect(jsonPath("$.data.usedCount").value(1));
        changeOrder(first, "CANCELLED").andExpect(status().isOk()); changeOrder(first, "CANCELLED").andExpect(status().isOk());
        perform(get(API + "/coupons/" + coupon + "/usages"), null).andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].released").value(true));
        perform(get(API + "/coupons/" + coupon), null).andExpect(jsonPath("$.data.usedCount").value(0));
        create("/orders", o).andExpect(status().isCreated());
        c.put("startAt", Instant.now().minusSeconds(200).toString()); c.put("endAt", Instant.now().minusSeconds(100).toString());
        perform(put(API + "/coupons/" + coupon), c).andExpect(status().isOk());
        create("/orders", o).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COUPON_NOT_ACTIVE"));
    }

    @Test
    void campaignsAndCouponCountersRollbackIfAnyStockReservationFails() throws Exception {
        UUID coupon = id(create("/coupons", coupon(10, "FIXED_AMOUNT")));
        UUID flash = id(create("/flash-sales", flash(50, 20)));
        Map<String, Object> o = order(11); o.put("applyMarketing", true); o.put("couponCode", "save-" + tag);
        create("/orders", o).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        perform(get(API + "/coupons/" + coupon), null).andExpect(jsonPath("$.data.usedCount").value(0));
        perform(get(API + "/flash-sales/" + flash), null).andExpect(jsonPath("$.data.items[0].soldQuantity").value(0));
        perform(get(API + "/coupons/" + coupon + "/usages"), null).andExpect(jsonPath("$.data.totalElements").value(0));
        assertStock(stock, 10, 0);
        assertThat(jdbc.queryForObject("select count(*) from orders where customer_id = ?", Integer.class, customer)).isZero();
    }

    @Test
    void concurrentCouponCheckoutsCannotExceedGlobalLimit() throws Exception {
        Map<String, Object> c = coupon(10, "FIXED_AMOUNT"); c.put("usageLimit", 1); c.put("usageLimitPerCustomer", 10);
        UUID coupon = id(create("/coupons", c));
        Map<String, Object> o = order(1); o.put("couponCode", "save-" + tag);
        concurrentOrders(o);
        perform(get(API + "/coupons/" + coupon), null).andExpect(jsonPath("$.data.usedCount").value(1));
        assertStock(stock, 10, 1);
    }

    @Test
    void concurrentFlashSalesUseSnapshotsAndCancellationRestoresCapacity() throws Exception {
        UUID flash = id(create("/flash-sales", flash(40, 1)));
        Map<String, Object> o = order(1); o.put("applyMarketing", true); concurrentOrders(o);
        UUID first = jdbc.queryForObject("select id from orders where customer_id = ?", UUID.class, customer);
        perform(get(API + "/orders/" + first), null).andExpect(jsonPath("$.data.totalAmount").value(40));
        perform(get(API + "/flash-sales/" + flash), null).andExpect(jsonPath("$.data.items[0].soldQuantity").value(1));
        changeOrder(first, "CANCELLED").andExpect(status().isOk()); changeOrder(first, "CANCELLED").andExpect(status().isOk());
        perform(get(API + "/flash-sales/" + flash), null).andExpect(jsonPath("$.data.items[0].remainingQuantity").value(1));
        Map<String, Object> f = flash(35, 1); perform(put(API + "/flash-sales/" + flash), f).andExpect(status().isOk());
        create("/orders", o).andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(35));
        perform(get(API + "/orders/" + first), null).andExpect(jsonPath("$.data.totalAmount").value(40));
        perform(delete(API + "/flash-sales/" + flash), null).andExpect(status().isOk());
        UUID second = jdbc.queryForObject("select id from orders where customer_id = ? and order_status = 'PLACED'", UUID.class, customer);
        changeOrder(second, "CANCELLED").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select sold_quantity from flash_sale_items where flash_sale_id = ?", Integer.class, flash)).isZero();
    }

    @Test
    void promotionScopePriorityAndBestFlashPriceAreAppliedWithoutStacking() throws Exception {
        Map<String, Object> p = promotion(20); UUID promotion = id(create("/promotions", p));
        Map<String, Object> o = order(2); o.put("applyMarketing", true); o.put("discountAmount", 10);
        UUID coupon = id(create("/coupons", coupon(10, "PERCENTAGE"))); o.put("couponCode", "save-" + tag);
        UUID first = id(create("/orders", o).andExpect(jsonPath("$.data.discountAmount").value(65)).andExpect(jsonPath("$.data.totalAmount").value(135)));
        p.put("discountValue", 80); p.put("productIds", List.of(UUID.randomUUID()));
        perform(put(API + "/promotions/" + promotion), p).andExpect(status().isNotFound());
        p.put("productIds", List.of(product)); p.put("promotionType", "ALL_PRODUCTS");
        perform(put(API + "/promotions/" + promotion), p).andExpect(status().isBadRequest());
        UUID flash = id(create("/flash-sales", flash(50, 5)));
        Map<String, Object> plain = order(1); plain.put("applyMarketing", true);
        create("/orders", plain).andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(50));
        // Default keeps explicit admin pricing compatible when marketing is not requested.
        create("/orders", order(1)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(100));
        perform(delete(API + "/promotions/" + promotion), null).andExpect(status().isOk());
        perform(get(API + "/orders/" + first), null).andExpect(jsonPath("$.data.totalAmount").value(135));
        perform(get(API + "/coupons/" + coupon), null).andExpect(jsonPath("$.data.usedCount").value(1));
        perform(get(API + "/flash-sales/" + flash), null).andExpect(jsonPath("$.data.items[0].soldQuantity").value(1));
    }

    @Test
    void flashUpdatesRejectInvalidPricesDuplicatesAndLimitsBelowSales() throws Exception {
        create("/flash-sales", flash(100, 2)).andExpect(status().isBadRequest());
        Map<String, Object> f = flash(50, 2); f.put("items", List.of(map("productVariantId", variant, "flashPrice", 50, "quantityLimit", 2), map("productVariantId", variant, "flashPrice", 60, "quantityLimit", 2)));
        create("/flash-sales", f).andExpect(status().isBadRequest());
        UUID flash = id(create("/flash-sales", flash(50, 2)));
        Map<String, Object> o = order(2); o.put("applyMarketing", true); create("/orders", o).andExpect(status().isCreated());
        perform(put(API + "/flash-sales/" + flash), flash(50, 1)).andExpect(status().isConflict());
        f = flash(50, 2); f.put("items", List.of(map("productVariantId", replacement, "flashPrice", 30, "quantityLimit", 2)));
        perform(put(API + "/flash-sales/" + flash), f).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FLASH_ITEM_CANNOT_BE_REMOVED"));
    }

    @Test
    void bannerCrudValidatesUrlsPeriodsAndFiltersPosition() throws Exception {
        Map<String, Object> b = banner(); UUID banner = id(create("/banners", b));
        perform(get(API + "/banners").param("position", "home").param("search", tag), null).andExpect(jsonPath("$.data.totalElements").value(1));
        b.put("linkUrl", "javascript:alert(1)"); perform(put(API + "/banners/" + banner), b).andExpect(status().isBadRequest());
        b.put("linkUrl", "/products"); b.put("startAt", Instant.now().toString()); b.put("endAt", Instant.now().minusSeconds(1).toString());
        perform(put(API + "/banners/" + banner), b).andExpect(status().isBadRequest());
        b.remove("startAt"); b.remove("endAt"); b.put("status", "INACTIVE");
        perform(put(API + "/banners/" + banner), b).andExpect(status().isOk());
        perform(get(API + "/banners").param("status", "ACTIVE").param("search", tag), null).andExpect(jsonPath("$.data.totalElements").value(0));
        perform(delete(API + "/banners/" + banner), null).andExpect(status().isOk());
        perform(get(API + "/banners/" + banner), null).andExpect(status().isNotFound());
    }

    @Test
    void reviewsRequireOwnedDeliveredItemsAndKeepImagesAfterArchive() throws Exception {
        UUID order = id(create("/orders", order(1))); UUID item = item(order);
        Map<String, Object> review = map("orderItemId", item, "rating", 5, "comment", "Review " + tag, "images", List.of("https://example.com/review.png"));
        asCustomer(post("/api/v1/me/product-reviews"), review, customerToken).andExpect(status().isConflict());
        UUID shipment = dispatch(order); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        asCustomer(post("/api/v1/me/product-reviews"), review, otherToken).andExpect(status().isConflict());
        UUID id = id(asCustomer(post("/api/v1/me/product-reviews"), review, customerToken).andExpect(jsonPath("$.data.status").value("PENDING")));
        asCustomer(post("/api/v1/me/product-reviews"), review, customerToken).andExpect(status().isConflict());
        perform(put(API + "/product-reviews/" + id + "/moderation"), map("status", "APPROVED", "note", "Verified"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.moderatedBy").exists());
        perform(get(API + "/product-reviews").param("productId", product.toString()).param("status", "APPROVED").param("rating", "5"), null)
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].images[0]").value("https://example.com/review.png"));
        asCustomer(get("/api/v1/me/product-reviews"), null, otherToken).andExpect(jsonPath("$.data.totalElements").value(0));
        perform(delete(API + "/product-reviews/" + id), null).andExpect(status().isOk());
        perform(get(API + "/product-reviews/" + id), null).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from review_images where review_id = ?", Integer.class, id)).isEqualTo(1);
        asCustomer(post("/api/v1/me/product-reviews"), review, customerToken).andExpect(status().isConflict());
    }

    @Test
    void targetedNotificationsPublishOnceKeepContentImmutableAndRestrictReadOwnership() throws Exception {
        Map<String, Object> n = notification("SELECTED", List.of(customer));
        UUID notification = id(create("/notifications", n).andExpect(jsonPath("$.data.status").value("DRAFT")));
        asCustomer(get("/api/v1/me/notifications"), null, customerToken).andExpect(jsonPath("$.data.totalElements").value(0));
        perform(post(API + "/notifications/" + notification + "/publish"), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.recipientCount").value(1));
        perform(post(API + "/notifications/" + notification + "/publish"), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.recipientCount").value(1));
        JsonNode inbox = body(asCustomer(get("/api/v1/me/notifications").param("read", "false"), null, customerToken));
        String inboxId = inbox.get("data").get("content").get(0).get("id").asString();
        asCustomer(put("/api/v1/me/notifications/" + inboxId + "/read"), null, otherToken).andExpect(status().isNotFound());
        JsonNode read = body(asCustomer(put("/api/v1/me/notifications/" + inboxId + "/read"), null, customerToken).andExpect(status().isOk()));
        asCustomer(put("/api/v1/me/notifications/" + inboxId + "/read"), null, customerToken).andExpect(jsonPath("$.data.readAt").value(read.get("data").get("readAt").asString()));
        perform(get(API + "/notifications/" + notification + "/recipients"), null).andExpect(jsonPath("$.data.content[0].read").value(true));
        perform(get(API + "/notifications/" + notification), null).andExpect(jsonPath("$.data.readCount").value(1));
        perform(put(API + "/notifications/" + notification), n).andExpect(status().isConflict());
        perform(delete(API + "/notifications/" + notification), null).andExpect(status().isConflict());
    }

    @Test
    void allNotificationsOnlyReachActiveCustomersAndInvalidTargetsRollback() throws Exception {
        jdbc.update("update customers set status = 'BLOCKED' where id = ?", otherCustomer);
        Map<String, Object> n = notification("SELECTED", List.of(otherCustomer)); UUID draft = id(create("/notifications", n));
        perform(post(API + "/notifications/" + draft + "/publish"), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_ACTIVE_RECIPIENTS"));
        perform(get(API + "/notifications/" + draft), null).andExpect(jsonPath("$.data.status").value("DRAFT"));
        perform(delete(API + "/notifications/" + draft), null).andExpect(status().isOk());
        create("/notifications", notification("ALL", List.of(customer))).andExpect(status().isBadRequest());
        create("/notifications", notification("SELECTED", List.of(UUID.randomUUID()))).andExpect(status().isNotFound());
        UUID all = id(create("/notifications", notification("ALL", List.of())));
        perform(post(API + "/notifications/" + all + "/publish"), null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from customer_notifications where notification_id = ? and customer_id = ?", Integer.class, all, customer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from customer_notifications where notification_id = ? and customer_id = ?", Integer.class, all, otherCustomer)).isZero();
    }

    @Test
    void allAdminModulesRequireAuthenticationAdminRoleAndTheirOwnReadPermission() throws Exception {
        for (String path : List.of("coupons", "promotions", "flash-sales", "banners", "notifications", "product-reviews")) {
            mvc.perform(get(API + "/" + path)).andExpect(status().isUnauthorized());
            asCustomer(get(API + "/" + path), null, customerToken).andExpect(status().isForbidden());
        }
        Map<String, String> permissionPaths = Map.of("COUPON_READ", "coupons", "PROMOTION_READ", "promotions", "FLASH_SALE_READ", "flash-sales", "BANNER_READ", "banners", "NOTIFICATION_READ", "notifications", "REVIEW_READ", "product-reviews");
        for (var p : permissionPaths.entrySet()) {
            UUID permission = jdbc.queryForObject("select id from permissions where code = ?", UUID.class, p.getKey());
            UUID role = jdbc.queryForObject("select id from roles where code = 'ADMIN'", UUID.class);
            jdbc.update("delete from role_permissions where role_id = ? and permission_id = ?", role, permission);
            try { perform(get(API + "/" + p.getValue()), null).andExpect(status().isForbidden()); }
            finally { jdbc.update("insert into role_permissions(role_id, permission_id) values(?,?) on conflict do nothing", role, permission); }
        }
        perform(get(API + "/coupons").param("size", "51"), null).andExpect(status().isBadRequest());
        perform(get(API + "/product-reviews").param("rating", "6"), null).andExpect(status().isBadRequest());
    }

    @Test
    void categoryPromotionsRespectPriorityAndUnusedFlashItemsCanBeRemoved() throws Exception {
        UUID category = jdbc.queryForObject("select category_id from products where id = ?", UUID.class, product);
        Map<String, Object> p = promotion(90); p.put("promotionType", "CATEGORY_DISCOUNT"); p.put("productIds", List.of()); p.put("categoryIds", List.of(category)); p.put("priority", 0);
        id(create("/promotions", p)); id(create("/promotions", promotion(10)));
        Map<String, Object> order = order(1); order.put("applyMarketing", true);
        create("/orders", order).andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(90));
        Map<String, Object> f = flash(40, 3);
        f.put("items", List.of(map("productVariantId", variant, "flashPrice", 40, "quantityLimit", 3), map("productVariantId", replacement, "flashPrice", 30, "quantityLimit", 3)));
        UUID flash = id(create("/flash-sales", f));
        perform(put(API + "/flash-sales/" + flash), flash(40, 3)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        assertThat(jdbc.queryForObject("select count(*) from flash_sale_items where flash_sale_id = ?", Integer.class, flash)).isEqualTo(1);
    }

    @Test
    void moduleWritePermissionsCannotBeReplacedByReadPermissions() throws Exception {
        Map<String, String> paths = Map.of("COUPON", "coupons", "PROMOTION", "promotions", "FLASH_SALE", "flash-sales", "BANNER", "banners", "NOTIFICATION", "notifications", "REVIEW", "product-reviews");
        Map<String, Object> requests = Map.of("COUPON", coupon(10, "FIXED_AMOUNT"), "PROMOTION", promotion(10), "FLASH_SALE", flash(50, 5), "BANNER", banner(),
                "NOTIFICATION", notification("ALL", List.of()), "REVIEW", map("orderItemId", UUID.randomUUID(), "rating", 4, "images", List.of()));
        for (var p : paths.entrySet()) {
            UUID permission = jdbc.queryForObject("select id from permissions where code = ?", UUID.class, p.getKey() + "_WRITE");
            UUID role = jdbc.queryForObject("select id from roles where code = 'ADMIN'", UUID.class);
            jdbc.update("delete from role_permissions where role_id = ? and permission_id = ?", role, permission);
            try {
                perform(post(API + "/" + p.getValue()).param("customerId", customer.toString()), requests.get(p.getKey())).andExpect(status().isForbidden());
                perform(get(API + "/" + p.getValue()), null).andExpect(status().isOk());
            } finally { jdbc.update("insert into role_permissions(role_id, permission_id) values(?,?) on conflict do nothing", role, permission); }
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
