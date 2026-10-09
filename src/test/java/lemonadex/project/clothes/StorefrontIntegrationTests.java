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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class StorefrontIntegrationTests extends PostgresTestSupport {
    private static final String PASSWORD = "Customer#2026";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private String admin;
    private UUID category, product, draft, small, large, stock;

    @BeforeEach
    void fixtures() throws Exception {
        admin = auth.login(new LoginRequest("admin", "admin123")).accessToken();
        category = id(adminCreate("/categories", map("name", "Áo " + tag, "status", "ACTIVE")));
        UUID child = id(adminCreate("/categories", map("name", "Sơ mi " + tag, "parentId", category, "status", "ACTIVE")));
        UUID brand = id(adminCreate("/brands", map("name", "Brand " + tag, "status", "ACTIVE")));
        UUID color = id(adminCreate("/colors", map("name", "Navy " + tag, "code", "N-" + tag, "hexCode", "#1F2A44", "status", "ACTIVE")));
        UUID s = id(adminCreate("/sizes", map("name", "S " + tag, "code", "S-" + tag, "sortOrder", 1, "status", "ACTIVE")));
        UUID l = id(adminCreate("/sizes", map("name", "L " + tag, "code", "L-" + tag, "sortOrder", 3, "status", "ACTIVE")));
        product = id(adminCreate("/products", map("productCode", "P-" + tag, "name", "Linen " + tag, "brandId", brand, "categoryId", child,
                "gender", "WOMEN", "basePrice", 450000, "status", "ACTIVE",
                "images", List.of(map("url", "https://example.com/" + tag + "-1.jpg", "altText", "Front"), map("url", "https://example.com/" + tag + "-2.jpg")))));
        draft = id(adminCreate("/products", map("productCode", "D-" + tag, "name", "Draft " + tag, "brandId", brand, "categoryId", child,
                "gender", "MEN", "basePrice", 100000, "status", "DRAFT", "images", List.of())));
        adminCreate("/product-variants", map("productId", draft, "colorId", color, "sizeId", s, "price", 100000, "status", "ACTIVE"));
        small = id(adminCreate("/product-variants", map("productId", product, "colorId", color, "sizeId", s, "price", 450000,
                "compareAtPrice", 520000, "status", "ACTIVE")));
        large = id(adminCreate("/product-variants", map("productId", product, "colorId", color, "sizeId", l, "price", 480000, "status", "ACTIVE")));
        UUID warehouse = id(adminCreate("/warehouses", map("name", "Store " + tag, "address", "Hồ Chí Minh", "status", "ACTIVE")));
        stock = id(adminCreate("/inventory", map("warehouseId", warehouse, "productVariantId", small)));
        adminCreate("/inventory", map("warehouseId", warehouse, "productVariantId", large));
        adminCreate("/inventory/transactions", map("warehouseId", warehouse, "productVariantId", small, "transactionType", "RECEIPT", "quantity", 5, "note", "Nhập hàng"));
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
        jdbc.update("delete from product_images where product_id in (select id from products where name like ?)", like);
        jdbc.update("delete from product_variants where product_id in (select id from products where name like ?)", like);
        jdbc.update("delete from products where name like ?", like);
        jdbc.update("delete from colors where name like ?", like);
        jdbc.update("delete from sizes where name like ?", like);
        jdbc.update("delete from brands where name like ?", like);
        jdbc.update("delete from categories where parent_id is not null and name like ?", like);
        jdbc.update("delete from categories where name like ?", like);
        jdbc.update("delete from customers where full_name like ?", like);
        jdbc.update("delete from banners where title like ?", like);
    }

    @Test
    void anonymousShoppersBrowseOnlyProductsOnSale() throws Exception {
        mvc.perform(get("/api/v1/store/catalog")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[*].name", hasItem("Sơ mi " + tag)))
                .andExpect(jsonPath("$.data.sizes[*].name", hasItem("L " + tag)));
        // Searching by the parent category includes products of its child category; the draft never shows.
        mvc.perform(get("/api/v1/store/products").param("search", tag).param("categoryId", category.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Linen " + tag))
                .andExpect(jsonPath("$.data.content[0].minPrice").value(450000))
                .andExpect(jsonPath("$.data.content[0].maxPrice").value(480000))
                .andExpect(jsonPath("$.data.content[0].compareAtPrice").value(520000))
                .andExpect(jsonPath("$.data.content[0].imageUrl").value("https://example.com/" + tag + "-1.jpg"))
                .andExpect(jsonPath("$.data.content[0].hoverImageUrl").value("https://example.com/" + tag + "-2.jpg"))
                .andExpect(jsonPath("$.data.content[0].colors[0].hexCode").value("#1F2A44"))
                .andExpect(jsonPath("$.data.content[0].inStock").value(true));
        mvc.perform(get("/api/v1/store/products").param("search", tag).param("gender", "MEN"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/store/products").param("search", tag).param("sort", "random")).andExpect(status().isBadRequest());

        String slug = json.readTree(mvc.perform(get("/api/v1/store/products/" + product)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants.length()").value(2))
                .andExpect(jsonPath("$.data.variants[?(@.sizeName == 'S " + tag + "')].available").value(hasItem(5)))
                .andExpect(jsonPath("$.data.variants[?(@.sizeName == 'L " + tag + "')].available").value(hasItem(0)))
                .andReturn().getResponse().getContentAsString()).path("data").path("slug").asString();
        mvc.perform(get("/api/v1/store/products/" + slug)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(product.toString()));
        mvc.perform(get("/api/v1/store/products/" + draft)).andExpect(status().isNotFound());

        adminCreate("/banners", map("title", "Hero " + tag, "imageUrl", "https://example.com/" + tag + ".jpg", "position", "HOME_" + tag,
                "sortOrder", 0, "status", "ACTIVE"));
        adminCreate("/banners", map("title", "Off " + tag, "imageUrl", "https://example.com/off.jpg", "position", "HOME_" + tag,
                "sortOrder", 1, "status", "INACTIVE"));
        mvc.perform(get("/api/v1/store/banners").param("position", "home_" + tag)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].title").value("Hero " + tag));
    }

    @Test
    void customersCheckOutViewAndCancelOnlyTheirOwnOrders() throws Exception {
        String shopper = customerToken("Thư " + tag);
        String other = customerToken("Bảo " + tag);
        Map<String, Object> checkout = map("recipientName", "Nguyễn Minh Thư", "recipientPhone", "0903418772",
                "shippingAddress", "214 Nguyễn Trãi, Quận 5", "items", List.of(map("productVariantId", small, "quantity", 2)),
                "paymentMethod", "COD");

        mvc.perform(withAuth(post("/api/v1/me/orders"), admin, checkout)).andExpect(status().isForbidden());
        Map<String, Object> tooMany = new LinkedHashMap<>(checkout);
        tooMany.put("items", List.of(map("productVariantId", large, "quantity", 1)));
        mvc.perform(withAuth(post("/api/v1/me/orders"), shopper, tooMany)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        String body = mvc.perform(withAuth(post("/api/v1/me/orders"), shopper, checkout)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.order.orderStatus").value("PLACED"))
                .andExpect(jsonPath("$.data.order.subtotal").value(900000))
                .andExpect(jsonPath("$.data.payments[0].paymentMethod").value("COD"))
                .andExpect(jsonPath("$.data.payments[0].status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        UUID order = UUID.fromString(json.readTree(body).path("data").path("order").path("id").asString());
        assertThat(jdbc.queryForObject("select quantity_reserved from inventories where id = ?", Integer.class, stock)).isEqualTo(2);

        mvc.perform(withAuth(get("/api/v1/me/orders"), shopper, null)).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(withAuth(get("/api/v1/me/orders"), other, null)).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(withAuth(get("/api/v1/me/orders/" + order), other, null)).andExpect(status().isNotFound());
        mvc.perform(withAuth(post("/api/v1/me/orders/" + order + "/cancel"), other, null)).andExpect(status().isNotFound());

        mvc.perform(withAuth(post("/api/v1/me/orders/" + order + "/cancel"), shopper, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.orderStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.payments[0].status").value("VOID"));
        assertThat(jdbc.queryForObject("select quantity_reserved from inventories where id = ?", Integer.class, stock)).isZero();
    }

    private String customerToken(String name) {
        String email = UUID.randomUUID() + "@example.com";
        auth.register(new RegisterRequest(email, PASSWORD, PASSWORD, name, null, null));
        return auth.login(new LoginRequest(email, PASSWORD)).accessToken();
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

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
