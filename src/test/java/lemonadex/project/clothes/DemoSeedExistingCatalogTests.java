package lemonadex.project.clothes;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo seed on a database whose admins already created colors, sizes and categories, including
 * a color code the seed would like to use but with another name, a differently coded grey and a
 * deleted color still holding a code. The seed must reuse by name, never borrow another color, and
 * keep its SKUs stable.
 */
@SpringBootTest @ActiveProfiles("test") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoSeedExistingCatalogTests {
    private static final Path SEED = Path.of("src/main/resources/db/seed/R__demo_seed.sql");

    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> SeedPostgres.url("seed_existing"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "postgres");
    }

    @BeforeAll
    void existingCatalogThenSeedTwice() throws IOException {
        jdbc.update("""
                INSERT INTO colors(name, code, hex_code, status, deleted) VALUES
                    ('Đen', 'BLACK', '#000000', 'ACTIVE', FALSE),
                    ('Xám', 'GRAY', '#808080', 'ACTIVE', FALSE),
                    ('Đỏ đô', 'NAVY', '#662929', 'ACTIVE', FALSE),
                    ('Denim cũ', 'DENIM', '#4A6A8F', 'ACTIVE', TRUE)
                """);
        jdbc.update("INSERT INTO sizes(name, code, sort_order, status) VALUES ('S', 'S', 1, 'ACTIVE'), ('M', 'M', 2, 'ACTIVE'), ('XXL', 'XXL', 5, 'ACTIVE')");
        jdbc.update("INSERT INTO categories(name, slug, status) VALUES ('Áo polo', 'ao-polo', 'ACTIVE'), ('Quần short', 'quan-short', 'ACTIVE')");
        String script = Files.readString(SEED);
        jdbc.execute(script);
        jdbc.execute(script);
    }

    @Test
    void seedReusesColorsByNameAndNeverBorrowsAnotherColorsCode() {
        assertThat(count("select count(*) from products where product_code like 'LXS-%'")).isEqualTo(63);
        assertThat(count("select count(*) from orders where order_code like 'LX-SEED%'")).isEqualTo(7);
        // "Xám" is reused although its code is GRAY; SKUs keep the seed's own codes.
        assertThat(text("select c.code from product_variants v join colors c on c.id = v.color_id where v.sku = 'LXS-HD01-GREY-L'")).isEqualTo("GRAY");
        // NAVY belongs to another color, so "Xanh navy" is created under a suffixed code and the existing one is untouched.
        assertThat(text("select c.code from product_variants v join colors c on c.id = v.color_id where v.sku = 'LXS-PL01-NAVY-L'")).isEqualTo("NAVY_LX");
        assertThat(text("select name from colors where code = 'NAVY'")).isEqualTo("Đỏ đô");
        // A deleted color still owns DENIM.
        assertThat(text("select c.code from product_variants v join colors c on c.id = v.color_id where v.sku = 'LXS-JN01-DENIM-L'")).isEqualTo("DENIM_LX");
        assertThat(count("select count(*) from colors where lower(name) = 'xám'")).isEqualTo(1);
        assertThat(count("select count(*) from categories where slug = 'ao-polo'")).isEqualTo(1);
        assertThat(count("select count(*) from product_reviews where status = 'APPROVED'")).isEqualTo(9);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private String text(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }

}
