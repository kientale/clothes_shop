package lemonadex.project.clothes;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.IOException;

/** A real, isolated PostgreSQL process shared by the Spring integration test contexts. */
abstract class PostgresTestSupport {
    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "postgres");
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().setPort(0).setRegisterShutdownHook(true).start();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
