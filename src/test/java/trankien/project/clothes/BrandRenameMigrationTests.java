package trankien.project.clothes;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BrandRenameMigrationTests {
    @Test
    void upgradeRebrandsExistingDemoDataAndKeepsTheSeedIdempotent() throws Exception {
        String url = SeedPostgres.url("brand_upgrade");
        Flyway.configure().dataSource(url, "postgres", "postgres").target("12").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "postgres"));
        String seed = Files.readString(Path.of("src/main/resources/db/seed/R__demo_seed.sql"));
        jdbc.execute(seed.replace("TranKien", "LemonadeX").replace("trankien", "lemonadex")
                .replace("TRANKIEN", "LEMONADEX"));
        String brandId = jdbc.queryForObject("select id::text from brands where slug = 'lemonadex-basics'", String.class);
        jdbc.update("update system_settings set setting_value = jsonb_set(setting_value::jsonb, '{supportPhone}', '\"0901234567\"')::text where setting_group = 'STORE'");

        Flyway.configure().dataSource(url, "postgres", "postgres").load().migrate();

        assertThat(jdbc.queryForObject("select setting_value::jsonb->>'storeName' from system_settings where setting_group = 'STORE'", String.class)).isEqualTo("TranKien");
        assertThat(jdbc.queryForObject("select setting_value::jsonb->>'supportEmail' from system_settings where setting_group = 'STORE'", String.class)).isEqualTo("hotro@trankien.vn");
        assertThat(jdbc.queryForObject("select setting_value::jsonb->>'supportPhone' from system_settings where setting_group = 'STORE'", String.class)).isEqualTo("0901234567");
        assertThat(jdbc.queryForObject("select id::text from brands where slug = 'trankien-basics'", String.class)).isEqualTo(brandId);
        assertThat(jdbc.queryForObject("select count(*) from banners where title ~* 'lemonadex'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from store_policies where content ~* 'lemonadex'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select configuration::text from payment_methods where code = 'BANK_TRANSFER'", String.class)).contains("TRANKIEN").doesNotContain("LEMONADEX");

        jdbc.execute(seed);
        assertThat(jdbc.queryForObject("select count(*) from brands", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from products", Integer.class)).isEqualTo(63);
        assertThat(jdbc.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from banners", Integer.class)).isEqualTo(5);
    }

    @Test
    void upgradePreservesACustomStoreName() {
        String url = SeedPostgres.url("custom_brand_upgrade");
        Flyway.configure().dataSource(url, "postgres", "postgres").target("12").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "postgres"));
        jdbc.update("update system_settings set setting_value = jsonb_set(setting_value::jsonb, '{storeName}', '\"Custom Store\"')::text where setting_group = 'STORE'");

        Flyway.configure().dataSource(url, "postgres", "postgres").load().migrate();

        assertThat(jdbc.queryForObject("select setting_value::jsonb->>'storeName' from system_settings where setting_group = 'STORE'", String.class)).isEqualTo("Custom Store");
    }
}
