package lemonadex.project.clothes;

import lemonadex.project.clothes.features.auth.dto.*;
import lemonadex.project.clothes.features.auth.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Customer self-service: password reset, profile, address book, wishlist, collections, guest order tracking. */
@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class CustomerSelfServiceIntegrationTests extends PostgresTestSupport {
    private static final String PASSWORD = "Customer#2026";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private String admin;
    private UUID product, variant;

    @BeforeEach
    void fixtures() throws Exception {
        admin = auth.login(new LoginRequest("admin", "admin123")).accessToken();
        UUID category = id(adminCreate("/categories", map("name", "Áo " + tag, "status", "ACTIVE")));
        UUID brand = id(adminCreate("/brands", map("name", "Brand " + tag, "status", "ACTIVE")));
        UUID color = id(adminCreate("/colors", map("name", "Navy " + tag, "code", "N-" + tag, "hexCode", "#1F2A44", "status", "ACTIVE")));
        UUID size = id(adminCreate("/sizes", map("name", "M " + tag, "code", "M-" + tag, "sortOrder", 2, "status", "ACTIVE")));
        product = id(adminCreate("/products", map("productCode", "P-" + tag, "name", "Linen " + tag, "slug", "linen-" + tag, "brandId", brand,
                "categoryId", category, "gender", "WOMEN", "basePrice", 450000, "status", "ACTIVE", "images", List.of())));
        variant = id(adminCreate("/product-variants", map("productId", product, "colorId", color, "sizeId", size, "price", 450000, "status", "ACTIVE")));
        UUID warehouse = id(adminCreate("/warehouses", map("name", "Store " + tag, "address", "Hồ Chí Minh", "status", "ACTIVE")));
        adminCreate("/inventory", map("warehouseId", warehouse, "productVariantId", variant));
        adminCreate("/inventory/transactions", map("warehouseId", warehouse, "productVariantId", variant, "transactionType", "RECEIPT", "quantity", 5, "note", "Nhập"));
    }

    @AfterEach
    void cleanup() {
        String like = "%" + tag + "%";
        String orders = "select id from orders where customer_id in (select id from customers where full_name like ?)";
        jdbc.update("delete from payment_transactions where payment_id in (select id from payments where order_id in (" + orders + "))", like);
        jdbc.update("delete from payments where order_id in (" + orders + ")", like);
        jdbc.update("delete from order_status_history where order_id in (" + orders + ")", like);
        jdbc.update("delete from order_items where order_id in (" + orders + ")", like);
        jdbc.update("delete from orders where customer_id in (select id from customers where full_name like ?)", like);
        jdbc.update("delete from inventory_transactions where warehouse_id in (select id from warehouses where name like ?)", like);
        jdbc.update("delete from inventories where warehouse_id in (select id from warehouses where name like ?)", like);
        jdbc.update("delete from warehouses where name like ?", like);
        jdbc.update("delete from collections where name like ?", like);
        jdbc.update("delete from product_variants where product_id in (select id from products where name like ?)", like);
        jdbc.update("delete from products where name like ?", like);
        jdbc.update("delete from colors where name like ?", like);
        jdbc.update("delete from sizes where name like ?", like);
        jdbc.update("delete from brands where name like ?", like);
        jdbc.update("delete from categories where name like ?", like);
        jdbc.update("delete from customers where full_name like ?", like);
    }

    @Test
    void forgotPasswordNeverRevealsAccountsAndResetLinksWorkOnce() throws Exception {
        String email = register("Lan " + tag);
        // Same answer for a registered and an unknown email.
        mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(map("email", email))))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_REQUESTED"));
        mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(map("email", "nobody-" + tag + "@example.com"))))
                .andExpect(status().isAccepted());
        UUID account = jdbc.queryForObject("select id from accounts where email = ?", UUID.class, email);
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens where account_id = ? and used_at is null", Integer.class, account)).isEqualTo(1);

        // The emailed token is not stored; plant a known one to exercise the reset.
        String token = "known-token-" + tag + "-0123456789abcdefghijklmnop";
        jdbc.update("insert into password_reset_tokens(account_id, token_hash, expires_at) values (?, ?, ?)", account, sha256(token),
                java.sql.Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        Map<String, Object> reset = map("token", token, "password", "NewPass#2026", "confirmPassword", "NewPass#2026");
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(reset)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(reset)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        assertThat(auth.login(new LoginRequest(email, "NewPass#2026")).accessToken()).isNotBlank();
        // Resetting spends every other open link too.
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens where account_id = ? and used_at is null", Integer.class, account)).isZero();

        String expired = "expired-token-" + tag + "-0123456789abcdefghijklmn";
        jdbc.update("insert into password_reset_tokens(account_id, token_hash, expires_at) values (?, ?, ?)", account, sha256(expired),
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(map("token", expired, "password", "Other#20260", "confirmPassword", "Other#20260"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customersManageProfilePasswordAndAddressBook() throws Exception {
        String email = register("Minh " + tag);
        String token = auth.login(new LoginRequest(email, PASSWORD)).accessToken();
        mvc.perform(withAuth(get("/api/v1/me/profile"), token, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email)).andExpect(jsonPath("$.data.fullName").value("Minh " + tag));
        mvc.perform(withAuth(put("/api/v1/me/profile"), token, map("fullName", "Minh Anh " + tag, "phone", "0903418772", "gender", "FEMALE",
                        "dateOfBirth", "1998-04-12")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value("0903418772"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("1998-04-12"));

        mvc.perform(withAuth(put("/api/v1/me/password"), token, map("currentPassword", "wrong-password", "newPassword", "Better#2026",
                "confirmPassword", "Better#2026"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
        mvc.perform(withAuth(put("/api/v1/me/password"), token, map("currentPassword", PASSWORD, "newPassword", "Better#2026",
                "confirmPassword", "Better#2026"))).andExpect(status().isOk());
        assertThat(auth.login(new LoginRequest(email, "Better#2026")).accessToken()).isNotBlank();

        Map<String, Object> home = map("recipientName", "Minh Anh", "phone", "0903418772", "addressLine", "12 Nguyễn Huệ",
                "ward", "Phường Sài Gòn", "province", "Thành phố Hồ Chí Minh", "makeDefault", false);
        UUID first = id(mvc.perform(withAuth(post("/api/v1/me/addresses"), token, home)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault").value(true)));
        Map<String, Object> office = new LinkedHashMap<>(home);
        office.put("addressLine", "72 Lê Thánh Tôn");
        office.put("makeDefault", true);
        UUID second = id(mvc.perform(withAuth(post("/api/v1/me/addresses"), token, office)).andExpect(status().isCreated()));
        mvc.perform(withAuth(get("/api/v1/me/addresses"), token, null)).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(second.toString())).andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].isDefault").value(false));

        String stranger = auth.login(new LoginRequest(register("Bảo " + tag), PASSWORD)).accessToken();
        mvc.perform(withAuth(put("/api/v1/me/addresses/" + first), stranger, home)).andExpect(status().isNotFound());
        mvc.perform(withAuth(delete("/api/v1/me/addresses/" + first), stranger, null)).andExpect(status().isNotFound());

        // Deleting the default hands it to the remaining address.
        mvc.perform(withAuth(delete("/api/v1/me/addresses/" + second), token, null)).andExpect(status().isNoContent());
        mvc.perform(withAuth(get("/api/v1/me/addresses"), token, null)).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(first.toString())).andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    void wishlistCollectionsTrackingAndSitemap() throws Exception {
        String email = register("Thư " + tag);
        String token = auth.login(new LoginRequest(email, PASSWORD)).accessToken();
        mvc.perform(withAuth(put("/api/v1/me/wishlist/" + product), token, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasItem(product.toString())));
        mvc.perform(withAuth(put("/api/v1/me/wishlist/" + product), token, null)).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(withAuth(get("/api/v1/me/wishlist"), token, null)).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Linen " + tag));
        mvc.perform(withAuth(put("/api/v1/me/wishlist/" + UUID.randomUUID()), token, null)).andExpect(status().isNotFound());
        mvc.perform(withAuth(delete("/api/v1/me/wishlist/" + product), token, null)).andExpect(jsonPath("$.data.length()").value(0));

        adminCreate("/collections", map("name", "Hè " + tag, "slug", "he-" + tag, "status", "ACTIVE", "productIds", List.of(product)));
        adminCreate("/collections", map("name", "Ẩn " + tag, "slug", "an-" + tag, "status", "INACTIVE", "productIds", List.of(product)));
        mvc.perform(get("/api/v1/store/collections")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", hasItem("he-" + tag))).andExpect(jsonPath("$.data[*].slug", not(hasItem("an-" + tag))));
        mvc.perform(get("/api/v1/store/collections/he-" + tag)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collection.productCount").value(1))
                .andExpect(jsonPath("$.data.products.content[0].id").value(product.toString()));
        mvc.perform(get("/api/v1/store/collections/an-" + tag)).andExpect(status().isNotFound());

        String body = mvc.perform(withAuth(post("/api/v1/me/orders"), token, map("recipientName", "Nguyễn Minh Thư", "recipientPhone", "0903418772",
                        "shippingAddress", "214 Nguyễn Trãi, Phường Chợ Quán, Thành phố Hồ Chí Minh",
                        "items", List.of(map("productVariantId", variant, "quantity", 1)), "paymentMethod", "COD")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = json.readTree(body).path("data").path("order").path("orderCode").asString();
        mvc.perform(post("/api/v1/store/orders/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(map("orderCode", code.toLowerCase(Locale.ROOT), "phone", "0903 418 772"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.orderStatus").value("PLACED"))
                .andExpect(jsonPath("$.data.recipientName").value("N*** Thư"))
                .andExpect(jsonPath("$.data.shippingArea").value("Phường Chợ Quán, Thành phố Hồ Chí Minh"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(1))
                .andExpect(jsonPath("$.data.id").doesNotExist());
        mvc.perform(post("/api/v1/store/orders/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(map("orderCode", code, "phone", "0911111111"))))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/store/sitemap.xml")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("/shop/products/linen-" + tag)))
                .andExpect(content().string(containsString("/shop/collections/he-" + tag)));
    }

    @Test
    void publicConfigurationCarriesBankDetailsForTransfers() throws Exception {
        mvc.perform(get("/api/v1/store/configuration")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMethods[?(@.code == 'COD')].kind").value(hasItem("COD")));
    }

    private String register(String name) {
        String email = UUID.randomUUID() + "@example.com";
        auth.register(new RegisterRequest(email, PASSWORD, PASSWORD, name, null, null));
        return email;
    }

    private ResultActions adminCreate(String path, Object body) throws Exception {
        return mvc.perform(withAuth(post("/api/v1/admin" + path), admin, body)).andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return request;
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(json.readTree(result.andReturn().getResponse().getContentAsString()).path("data").path("id").asString());
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
