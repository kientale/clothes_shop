package lemonadex.project.clothes;

import lemonadex.project.clothes.features.auth.dto.LoginRequest;
import lemonadex.project.clothes.features.auth.service.AuthService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Loads db/seed/R__demo_seed.sql through Flyway (as FLYWAY_LOCATIONS does in .env) on its own PostgreSQL,
 * so other test classes keep a clean database, runs it once more by hand, and checks the result
 * through the real storefront and customer APIs.
 */
@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoSeedIntegrationTests {
    private static final Path SEED = Path.of("src/main/resources/db/seed/R__demo_seed.sql");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> SeedPostgres.url("seed_flyway"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "postgres");
        properties.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/seed");
    }

    @BeforeAll
    void seedAgain() throws IOException {
        assertThat(count("select count(*) from flyway_schema_history where script = 'R__demo_seed.sql' and success")).isEqualTo(1);
        // A second run (e.g. after the file changes) must not duplicate anything.
        jdbc.execute(Files.readString(SEED));
    }

    @Test
    void seedIsIdempotentAndKeepsTheStockLedgerConsistent() {
        assertThat(count("select count(*) from products where product_code like 'LXS-%'")).isEqualTo(63);
        assertThat(count("select count(*) from orders where order_code like 'LX-SEED%'")).isEqualTo(7);
        assertThat(count("select count(*) from accounts where email like '%@example.com' and email <> 'admin@example.com'")).isEqualTo(4);
        assertThat(count("select count(*) from banners where position in ('HOME_HERO', 'INTRO_HERO', 'INTRO_STORY')")).isEqualTo(5);
        assertThat(count("select count(*) from store_policies where status = 'ACTIVE'")).isEqualTo(6);
        assertThat(count("select count(*) from product_reviews where status = 'APPROVED'")).isEqualTo(9);
        // Every stock row equals the totals after its latest ledger movement.
        assertThat(count("""
                select count(*) from inventories i
                where (i.quantity_on_hand > 0 or i.quantity_reserved > 0 or exists (select 1 from inventory_transactions t
                      where t.warehouse_id = i.warehouse_id and t.product_variant_id = i.product_variant_id))
                  and not exists (select 1 from inventory_transactions t
                      where t.warehouse_id = i.warehouse_id and t.product_variant_id = i.product_variant_id
                        and t.quantity_after = i.quantity_on_hand and t.reserved_after = i.quantity_reserved
                        and t.created_at = (select max(created_at) from inventory_transactions u
                            where u.warehouse_id = i.warehouse_id and u.product_variant_id = i.product_variant_id))
                """)).isZero();
        // The placed order still holds its reservation; shipped ones have left the warehouse.
        assertThat(count("select coalesce(sum(quantity_reserved), 0) from inventories")).isEqualTo(2);
        assertThat(count("select count(*) from orders where order_code like 'LX-SEED%' and total_amount <> subtotal - discount_amount + shipping_fee")).isZero();
    }

    @Test
    void storefrontServesTheSeededCatalog() throws Exception {
        mvc.perform(get("/api/v1/store/products").param("size", "60")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(63))
                .andExpect(jsonPath("$.data.content[0].imageUrl", startsWith("https://images.pexels.com/photos/")));
        mvc.perform(get("/api/v1/store/products/ao-so-mi-linen-co-tru")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(2))
                .andExpect(jsonPath("$.data.ratingCount").value(1))
                .andExpect(jsonPath("$.data.reviews[0].customerName").value("Minh Thư N."))
                .andExpect(jsonPath("$.data.variants[?(@.sku == 'LXS-SM01-BEIGE-L')].available").value(hasItem(0)));
        mvc.perform(get("/api/v1/store/banners").param("position", "INTRO_STORY")).andExpect(jsonPath("$.data.length()").value(3));
        mvc.perform(get("/api/v1/content/store-policies/RETURN_EXCHANGE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Chính sách đổi trả"));
        mvc.perform(get("/api/v1/content/articles").param("articleType", "LOOKBOOK")).andExpect(jsonPath("$.data.totalElements").value(3));
        mvc.perform(get("/api/v1/store/configuration")).andExpect(jsonPath("$.data.store.supportEmail").value("hotro@lemonadex.vn"))
                .andExpect(jsonPath("$.data.shippingMethods.length()").value(2));
    }

    @Test
    void seededCustomersSignInAndSeeTheirOrders() throws Exception {
        String token = auth.login(new LoginRequest("minhthu.nguyen@example.com", "Khachhang@2026")).accessToken();
        mvc.perform(get("/api/v1/me/orders").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].orderStatus", containsInAnyOrder("DELIVERED", "COMPLETED")))
                .andExpect(jsonPath("$.data.content[*].paymentStatus", everyItem(is("PAID"))));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

}
