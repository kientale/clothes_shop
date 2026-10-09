package lemonadex.project.clothes;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One PostgreSQL process for the demo-seed tests, separate from {@link PostgresTestSupport} so the seed
 * never reaches the database other tests share. Each seed test class gets its own database in it;
 * starting a process per class is avoided because initdb fails on Windows with several running at once.
 */
final class SeedPostgres {
    private static final EmbeddedPostgres POSTGRES = start();

    private SeedPostgres() {}

    /** JDBC URL of a fresh database with the given name, created on first use. */
    static synchronized String url(String database) {
        try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement statement = connection.createStatement()) {
            var exists = statement.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + database + "'");
            if (!exists.next()) statement.execute("CREATE DATABASE " + database);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot create test database " + database, ex);
        }
        return POSTGRES.getJdbcUrl("postgres", database);
    }

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().setPort(0).setRegisterShutdownHook(true).start();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
