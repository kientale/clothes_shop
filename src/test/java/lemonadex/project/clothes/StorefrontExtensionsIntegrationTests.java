package lemonadex.project.clothes;

import lemonadex.project.clothes.features.auth.dto.*;
import lemonadex.project.clothes.features.auth.dto.SocialLoginRequests.VerifiedIdentity;
import lemonadex.project.clothes.features.auth.service.AuthService;
import lemonadex.project.clothes.features.auth.service.SocialTokenVerifier;
import lemonadex.project.clothes.features.storefront.service.StockAlertService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Storefront extensions: guest checkout, server-side cart, customer returns, email verification, social sign-in,
 * back-in-stock alerts, search suggestions, size charts, customer uploads, VNPay payments and GHTK callbacks.
 */
@SpringBootTest(properties = {
        "app.payments.vnpay.tmn-code=TESTTMN",
        "app.payments.vnpay.hash-secret=TESTSECRETTESTSECRET",
        "app.carriers.ghtk.webhook-secret=ghtk-test-secret",
        "app.social.google-client-id=test-client.apps.googleusercontent.com"})
@ActiveProfiles("test") @AutoConfigureMockMvc
class StorefrontExtensionsIntegrationTests extends PostgresTestSupport {
    private static final String PASSWORD = "Customer#2026";
    private static final String VNPAY_SECRET = "TESTSECRETTESTSECRET";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired StockAlertService stockAlerts;
    @MockitoBean SocialTokenVerifier verifier;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private String admin;
    private UUID category, product, variant, otherSize, warehouse;

    @BeforeEach
    void fixtures() throws Exception {
        admin = auth.login(new LoginRequest("admin", "admin123")).accessToken();
        category = id(adminCreate("/categories", map("name", "Áo khoác " + tag, "status", "ACTIVE")));
        UUID brand = id(adminCreate("/brands", map("name", "Brand " + tag, "status", "ACTIVE")));
        UUID color = id(adminCreate("/colors", map("name", "Navy " + tag, "code", "N-" + tag, "hexCode", "#1F2A44", "status", "ACTIVE")));
        UUID medium = id(adminCreate("/sizes", map("name", "M " + tag, "code", "M-" + tag, "sortOrder", 2, "status", "ACTIVE")));
        UUID large = id(adminCreate("/sizes", map("name", "L " + tag, "code", "L-" + tag, "sortOrder", 3, "status", "ACTIVE")));
        product = id(adminCreate("/products", map("productCode", "P-" + tag, "name", "Linen " + tag, "slug", "linen-" + tag, "brandId", brand,
                "categoryId", category, "gender", "WOMEN", "basePrice", 450000, "status", "ACTIVE", "images", List.of())));
        variant = id(adminCreate("/product-variants", map("productId", product, "colorId", color, "sizeId", medium, "price", 450000, "status", "ACTIVE")));
        otherSize = id(adminCreate("/product-variants", map("productId", product, "colorId", color, "sizeId", large, "price", 450000, "status", "ACTIVE")));
        warehouse = id(adminCreate("/warehouses", map("name", "Store " + tag, "address", "Hồ Chí Minh", "status", "ACTIVE")));
        adminCreate("/inventory", map("warehouseId", warehouse, "productVariantId", variant));
        adminCreate("/inventory", map("warehouseId", warehouse, "productVariantId", otherSize));
        receive(variant, 10);
    }

    @AfterEach
    void cleanup() {
        String like = "%" + tag + "%";
        String orders = "select id from orders where customer_id in (select id from customers where full_name like ?)";
        String returned = "select id from return_requests where order_id in (" + orders + ")";
        String payments = "select id from payments where order_id in (" + orders + ")";
        String shipments = "select id from shipments where order_id in (" + orders + ")";
        jdbc.update("update payment_methods set is_enabled = false where code = 'VNPAY'");
        jdbc.update("update shipping_methods set is_enabled = false where code = 'GHTK'");
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
        jdbc.update("delete from product_variants where product_id in (select id from products where name like ?)", like);
        jdbc.update("delete from products where name like ?", like);
        jdbc.update("delete from colors where name like ?", like);
        jdbc.update("delete from sizes where name like ?", like);
        jdbc.update("delete from brands where name like ?", like);
        jdbc.update("delete from categories where name like ?", like);
        jdbc.update("delete from customers where full_name like ?", like);
    }

    @Test
    void guestsCheckOutWithoutAnAccountAndFollowTheirOrder() throws Exception {
        Map<String, Object> order = checkout("Khách " + tag, "0912 345 678");
        order.remove("email");
        mvc.perform(json(post("/api/v1/store/orders"), order)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_REQUIRED"));

        order.put("email", "Guest-" + tag + "@Example.com");
        String code = body(mvc.perform(json(post("/api/v1/store/orders"), order)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderStatus").value("PLACED"))
                .andExpect(jsonPath("$.data.payments[0].paymentMethod").value("COD"))
                .andExpect(jsonPath("$.data.id").doesNotExist())).path("data").path("orderCode").asString();
        assertThat(jdbc.queryForObject("select contact_email from orders where order_code = ?", String.class, code))
                .isEqualTo("guest-" + tag + "@example.com");
        UUID guest = jdbc.queryForObject("select customer_id from orders where order_code = ?", UUID.class, code);
        assertThat(jdbc.queryForObject("select account_id from customers where id = ?", UUID.class, guest)).isNull();

        // The same phone finds the same guest customer again.
        String second = body(mvc.perform(json(post("/api/v1/store/orders"), order)).andExpect(status().isCreated())).path("data").path("orderCode").asString();
        assertThat(jdbc.queryForObject("select customer_id from orders where order_code = ?", UUID.class, second)).isEqualTo(guest);

        mvc.perform(json(post("/api/v1/store/orders/lookup"), map("orderCode", code, "phone", "0912345678"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(450000)).andExpect(jsonPath("$.data.paidAmount").value(0));
        // Paying online needs the gateway method switched on.
        mvc.perform(json(post("/api/v1/store/orders/pay"), map("orderCode", code, "phone", "0912345678", "method", "MOMO")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"));
    }

    @Test
    void signedInCustomersKeepTheirCartOnTheServer() throws Exception {
        String token = token(register("Cart " + tag));
        mvc.perform(auth(get("/api/v1/me/cart"), token, null)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(auth(put("/api/v1/me/cart"), token, map("items", List.of(map("productVariantId", variant, "quantity", 1),
                        map("productVariantId", variant, "quantity", 2), map("productVariantId", otherSize, "quantity", 1),
                        map("productVariantId", UUID.randomUUID(), "quantity", 1)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.variantId == '" + variant + "')].quantity").value(hasItem(3)))
                .andExpect(jsonPath("$.data[?(@.variantId == '" + variant + "')].name").value(hasItem("Linen " + tag)))
                .andExpect(jsonPath("$.data[?(@.variantId == '" + otherSize + "')].available").value(hasItem(0)));
        mvc.perform(auth(put("/api/v1/me/cart"), token, map("items", List.of()))).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void customersAskForReturnsAndExchangesOnDeliveredOrders() throws Exception {
        receive(otherSize, 3);
        String token = token(register("Return " + tag));
        JsonNode placed = body(mvc.perform(auth(post("/api/v1/me/orders"), token, checkout("Return " + tag, "0903418772"))).andExpect(status().isCreated()));
        UUID order = UUID.fromString(placed.path("data").path("order").path("id").asString());
        UUID item = UUID.fromString(placed.path("data").path("order").path("items").get(0).path("id").asString());

        Map<String, Object> request = map("orderId", order, "requestType", "RETURN", "reason", "Không vừa",
                "items", List.of(map("orderItemId", item, "quantity", 1)), "images", List.of());
        mvc.perform(auth(post("/api/v1/me/returns"), token, request)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_DELIVERED"));
        deliver(order);

        mvc.perform(auth(get("/api/v1/me/orders/" + order + "/items/" + item + "/exchange-options"), token, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].variantId").value(otherSize.toString())).andExpect(jsonPath("$.data[0].available").value(3));
        UUID exchange = id(mvc.perform(auth(post("/api/v1/me/returns"), token, map("orderId", order, "requestType", "EXCHANGE", "reason", "Đổi size",
                        "items", List.of(map("orderItemId", item, "quantity", 1, "replacementVariantId", otherSize)),
                        "images", List.of("https://cdn.example.com/a.jpg"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.images[0]").value("https://cdn.example.com/a.jpg")));
        // The item is fully claimed by the open exchange.
        mvc.perform(auth(post("/api/v1/me/returns"), token, request)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_QUANTITY_EXCEEDED"));

        String stranger = token(register("Stranger " + tag));
        mvc.perform(auth(get("/api/v1/me/returns/" + exchange), stranger, null)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/v1/me/returns"), stranger, request)).andExpect(status().isNotFound());

        mvc.perform(auth(get("/api/v1/me/returns").param("orderId", order.toString()), token, null))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(auth(post("/api/v1/me/returns/" + exchange + "/cancel"), token, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(auth(post("/api/v1/me/returns"), token, request)).andExpect(status().isCreated());
    }

    @Test
    void newAccountsConfirmTheirEmailWithAOneTimeLink() throws Exception {
        String email = register("Verify " + tag);
        String token = token(email);
        mvc.perform(auth(get("/api/v1/auth/me"), token, null)).andExpect(jsonPath("$.data.emailVerified").value(false));
        UUID account = jdbc.queryForObject("select id from accounts where email = ?", UUID.class, email);
        assertThat(jdbc.queryForObject("select count(*) from email_verification_tokens where account_id = ? and used_at is null", Integer.class, account)).isEqualTo(1);

        mvc.perform(auth(post("/api/v1/auth/email/resend"), token, null)).andExpect(status().isAccepted());
        String known = "verify-token-" + tag + "-0123456789abcdefghijklmnop";
        jdbc.update("insert into email_verification_tokens(account_id, token_hash, expires_at) values (?, ?, ?)", account, sha256(known),
                Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)));
        mvc.perform(json(post("/api/v1/auth/email/verify"), map("token", known))).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/auth/email/verify"), map("token", known))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
        mvc.perform(auth(get("/api/v1/auth/me"), token, null)).andExpect(jsonPath("$.data.emailVerified").value(true));
        mvc.perform(auth(post("/api/v1/auth/email/resend"), token, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    void googleSignInCreatesOrLinksCustomerAccountsButNeverStaff() throws Exception {
        mvc.perform(get("/api/v1/auth/providers")).andExpect(jsonPath("$.data.googleClientId").value("test-client.apps.googleusercontent.com"))
                .andExpect(jsonPath("$.data.facebookAppId").doesNotExist());
        String email = "social-" + tag + "@example.com";
        when(verifier.google(anyString())).thenReturn(new VerifiedIdentity("GOOGLE", "sub-" + tag, email, true, "Google " + tag));
        mvc.perform(json(post("/api/v1/auth/google"), map("credential", "id-token"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.email").value(email)).andExpect(jsonPath("$.data.account.emailVerified").value(true))
                .andExpect(jsonPath("$.data.account.roles", hasItem("CUSTOMER")));
        mvc.perform(json(post("/api/v1/auth/google"), map("credential", "id-token"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from account_identities where subject = ?", Integer.class, "sub-" + tag)).isEqualTo(1);

        // An existing customer is linked by the verified email.
        String existing = register("Linked " + tag);
        when(verifier.google(anyString())).thenReturn(new VerifiedIdentity("GOOGLE", "sub2-" + tag, existing, true, "Linked"));
        mvc.perform(json(post("/api/v1/auth/google"), map("credential", "id-token"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.email").value(existing));

        when(verifier.google(anyString())).thenReturn(new VerifiedIdentity("GOOGLE", "sub3-" + tag, "x-" + tag + "@example.com", false, "X"));
        mvc.perform(json(post("/api/v1/auth/google"), map("credential", "id-token"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SOCIAL_EMAIL_REQUIRED"));
        when(verifier.google(anyString())).thenReturn(new VerifiedIdentity("GOOGLE", "sub4-" + tag, "admin@example.com", true, "Admin"));
        mvc.perform(json(post("/api/v1/auth/google"), map("credential", "id-token"))).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/auth/facebook"), map("accessToken", "token"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_UNAVAILABLE"));
    }

    @Test
    void shoppersAreEmailedOnceWhenASoldOutSizeIsBack() throws Exception {
        String email = "wait-" + tag + "@example.com";
        mvc.perform(json(post("/api/v1/store/stock-alerts"), map("productVariantId", variant, "email", email)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VARIANT_IN_STOCK"));
        mvc.perform(json(post("/api/v1/store/stock-alerts"), map("productVariantId", otherSize, "email", email))).andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/store/stock-alerts"), map("productVariantId", otherSize, "email", email.toUpperCase(Locale.ROOT))))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from stock_alerts where product_variant_id = ?", Integer.class, otherSize)).isEqualTo(1);
        mvc.perform(json(post("/api/v1/store/stock-alerts"), map("productVariantId", UUID.randomUUID(), "email", email))).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/v1/admin/stock-alerts"), admin, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.productVariantId == '" + otherSize + "')].waiting").value(hasItem(1)));

        stockAlerts.notifyRestocked();
        assertThat(jdbc.queryForObject("select notified_at from stock_alerts where product_variant_id = ?", Timestamp.class, otherSize)).isNull();
        receive(otherSize, 2);
        assertThat(stockAlerts.notifyRestocked()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select notified_at from stock_alerts where product_variant_id = ?", Timestamp.class, otherSize)).isNotNull();
    }

    @Test
    void searchSuggestionsSizeChartsAndCustomerUploads() throws Exception {
        mvc.perform(get("/api/v1/store/search/suggest").param("q", "linen " + tag)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products[0].id").value(product.toString()));
        // Category names match without Vietnamese accents.
        mvc.perform(get("/api/v1/store/search/suggest").param("q", "ao khoac " + tag))
                .andExpect(jsonPath("$.data.categories[0].id").value(category.toString()));

        Map<String, Object> chart = map("columns", List.of("Size", "Ngực (cm)"), "rows", List.of(List.of("M", "88-92"), List.of("L", "92-96")), "note", "Đo sát người");
        mvc.perform(auth(put("/api/v1/admin/products/" + product + "/size-chart"), admin,
                        map("columns", List.of("Size", "Ngực"), "rows", List.of(List.of("M")), "note", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIZE_CHART_SHAPE"));
        mvc.perform(auth(put("/api/v1/admin/products/" + product + "/size-chart"), admin, chart)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/store/products/linen-" + tag)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sizeChart.columns[1]").value("Ngực (cm)")).andExpect(jsonPath("$.data.sizeChart.rows[1][0]").value("L"));
        mvc.perform(auth(delete("/api/v1/admin/products/" + product + "/size-chart"), admin, null)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/store/products/linen-" + tag)).andExpect(jsonPath("$.data.sizeChart").doesNotExist());

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
        String token = token(register("Upload " + tag));
        mvc.perform(multipart("/api/v1/me/uploads/images").file(new MockMultipartFile("file", "review.png", "image/png", png))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.url", containsString("/api/v1/files/")));
        mvc.perform(multipart("/api/v1/me/uploads/images").file(new MockMultipartFile("file", "review.png", "image/png", png))
                .header("Authorization", "Bearer " + admin)).andExpect(status().isForbidden());
    }

    @Test
    void vnpayPaymentsAreSettledOnceBySignedCallbacks() throws Exception {
        jdbc.update("update payment_methods set is_enabled = true where code = 'VNPAY'");
        String token = token(register("Pay " + tag));
        Map<String, Object> order = checkout("Pay " + tag, "0903418772");
        order.put("paymentMethod", "VNPAY");
        UUID orderId = UUID.fromString(body(mvc.perform(auth(post("/api/v1/me/orders"), token, order)).andExpect(status().isCreated()))
                .path("data").path("order").path("id").asString());
        String payUrl = body(mvc.perform(auth(post("/api/v1/me/orders/" + orderId + "/pay"), token, map("method", "VNPAY")))
                .andExpect(status().isOk())).path("data").path("payUrl").asString();
        assertThat(payUrl).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        Map<String, String> sent = UriComponentsBuilder.fromUriString(payUrl).build().getQueryParams().toSingleValueMap();
        assertThat(sent).containsEntry("vnp_Amount", "45000000").containsEntry("vnp_TmnCode", "TESTTMN");
        String reference = sent.get("vnp_TxnRef");

        Map<String, String> callback = new TreeMap<>(Map.of("vnp_TxnRef", reference, "vnp_Amount", "45000000", "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00", "vnp_TransactionNo", "1400" + tag.hashCode(), "vnp_TmnCode", "TESTTMN", "vnp_OrderInfo", "Thanh toan don hang"));
        Map<String, String> forged = new TreeMap<>(callback);
        forged.put("vnp_SecureHash", "00ff");
        mvc.perform(ipn(forged)).andExpect(jsonPath("$.RspCode").value("97"));
        Map<String, String> wrongAmount = new TreeMap<>(callback);
        wrongAmount.put("vnp_Amount", "100");
        mvc.perform(ipn(signed(wrongAmount))).andExpect(jsonPath("$.RspCode").value("04"));

        mvc.perform(ipn(signed(callback))).andExpect(jsonPath("$.RspCode").value("00"));
        mvc.perform(ipn(signed(callback))).andExpect(jsonPath("$.RspCode").value("02"));
        mvc.perform(auth(get("/api/v1/me/orders/" + orderId), token, null)).andExpect(jsonPath("$.data.order.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.payments[0].status").value("PAID"));
        // The shopper's return to the shop shows the settled result without settling twice.
        mvc.perform(json(post("/api/v1/payments/vnpay/return"), signed(callback))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID")).andExpect(jsonPath("$.data.orderId").value(orderId.toString()));
        mvc.perform(auth(post("/api/v1/me/orders/" + orderId + "/pay"), token, map("method", "VNPAY"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_PAID"));
    }

    @Test
    void ghtkCallbacksMoveShipmentsThroughTheNormalWorkflow() throws Exception {
        String token = token(register("Ship " + tag));
        UUID order = UUID.fromString(body(mvc.perform(auth(post("/api/v1/me/orders"), token, checkout("Ship " + tag, "0903418772")))
                .andExpect(status().isCreated())).path("data").path("order").path("id").asString());
        mvc.perform(auth(put("/api/v1/admin/orders/" + order + "/status"), admin, map("status", "CONFIRMED"))).andExpect(status().isOk());
        // Without a GHTK token the shipment cannot be created at GHTK...
        mvc.perform(auth(post("/api/v1/admin/carriers/ghtk/shipments"), admin, map("orderId", order))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CARRIER_NOT_CONFIGURED"));
        // ...so record it by hand with a GHTK label, as staff would after booking it on the GHTK site.
        String label = "S1.A1." + tag;
        UUID shipment = id(mvc.perform(auth(post("/api/v1/admin/shipments"), admin, map("orderId", order, "shippingProvider", "GHTK",
                "trackingCode", label, "shippingFee", 0))).andExpect(status().isCreated()));

        mvc.perform(post("/api/v1/carriers/ghtk/webhook").param("hash", "wrong").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("label_id", label).param("status_id", "3")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/carriers/ghtk/webhook").param("hash", "ghtk-test-secret").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("label_id", label).param("status_id", "4")).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/admin/shipments/" + shipment), admin, null)).andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));
        mvc.perform(post("/api/v1/carriers/ghtk/webhook?hash=ghtk-test-secret").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map("label_id", label, "status_id", 5, "reason", "")))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/admin/orders/" + order), admin, null)).andExpect(jsonPath("$.data.orderStatus").value("DELIVERED"));
        // Unknown labels and statuses that change nothing are ignored.
        mvc.perform(post("/api/v1/carriers/ghtk/webhook").param("hash", "ghtk-test-secret").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("label_id", "nope").param("status_id", "5")).andExpect(status().isOk());

        // Without a token the GHTK method charges its base fee.
        jdbc.update("update shipping_methods set is_enabled = true where code = 'GHTK'");
        mvc.perform(json(post("/api/v1/store/shipping/quote"), map("shippingMethodCode", "GHTK",
                        "area", map("province", "Hà Nội", "ward", "Phường Hoàn Kiếm", "street", "1 Tràng Tiền"),
                        "items", List.of(map("productVariantId", variant, "quantity", 1)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shippingFee").value(30000)).andExpect(jsonPath("$.data.live").value(false));
    }

    private Map<String, Object> checkout(String name, String phone) {
        return map("recipientName", name, "recipientPhone", phone.replace(" ", ""), "email", "buyer-" + tag + "@example.com",
                "shippingAddress", "214 Nguyễn Trãi, Phường Chợ Quán, Thành phố Hồ Chí Minh",
                "items", List.of(map("productVariantId", variant, "quantity", 1)), "paymentMethod", "COD");
    }

    private void deliver(UUID order) throws Exception {
        mvc.perform(auth(put("/api/v1/admin/orders/" + order + "/status"), admin, map("status", "CONFIRMED"))).andExpect(status().isOk());
        UUID shipment = id(mvc.perform(auth(post("/api/v1/admin/shipments"), admin, map("orderId", order, "shippingProvider", "Local " + tag,
                "trackingCode", UUID.randomUUID().toString(), "shippingFee", 0))).andExpect(status().isCreated()));
        for (String next : List.of("SHIPPED", "DELIVERED"))
            mvc.perform(auth(put("/api/v1/admin/shipments/" + shipment + "/status"), admin, map("status", next))).andExpect(status().isOk());
    }

    private void receive(UUID productVariant, int quantity) throws Exception {
        adminCreate("/inventory/transactions", map("warehouseId", warehouse, "productVariantId", productVariant, "transactionType", "RECEIPT",
                "quantity", quantity, "note", "Nhập " + tag));
    }

    private MockHttpServletRequestBuilder ipn(Map<String, String> params) {
        MockHttpServletRequestBuilder request = get("/api/v1/payments/vnpay/ipn");
        params.forEach(request::param);
        return request;
    }

    /** Signs callback parameters the way VNPay does: sorted, URL-encoded, HMAC-SHA512. */
    private static Map<String, String> signed(Map<String, String> params) throws Exception {
        StringJoiner data = new StringJoiner("&");
        new TreeMap<>(params).forEach((k, v) -> data.add(URLEncoder.encode(k, StandardCharsets.US_ASCII) + "=" + URLEncoder.encode(v, StandardCharsets.US_ASCII)));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(VNPAY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        Map<String, String> result = new TreeMap<>(params);
        result.put("vnp_SecureHash", HexFormat.of().formatHex(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8))));
        return result;
    }

    private String register(String name) {
        String email = UUID.randomUUID() + "@example.com";
        auth.register(new RegisterRequest(email, PASSWORD, PASSWORD, name, null, null));
        return email;
    }

    private String token(String email) {
        return auth.login(new LoginRequest(email, PASSWORD)).accessToken();
    }

    private ResultActions adminCreate(String path, Object body) throws Exception {
        return mvc.perform(auth(post("/api/v1/admin" + path), admin, body)).andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return request;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(body(result).path("data").path("id").asString());
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
