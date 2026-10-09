package lemonadex.project.clothes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in browser tests using the real HTTP API and an isolated test database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.cors.allowed-origins=http://127.0.0.1:3400,http://127.0.0.1:3401"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_FRONTEND_AUTH_E2E", matches = "true")
class FrontendAuthIntegrationTests extends PostgresTestSupport {
    @LocalServerPort
    private int port;

    @Test
    void frontendAuthenticatesAgainstRealBackend() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path log = root.resolve("target/frontend-auth-e2e.log");
        Files.createDirectories(log.getParent());
        ProcessBuilder builder = new ProcessBuilder("node", "node_modules/@playwright/test/cli.js",
                "test", "--config", "playwright.backend.config.mjs")
                .directory(root.resolve("frontend-admin").toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        builder.environment().put("VITE_API_PROXY_TARGET", "http://127.0.0.1:" + port);
        Process browser = builder.start();
        try {
            assertThat(browser.waitFor(180, TimeUnit.SECONDS))
                    .withFailMessage("Browser suite timed out. See %s", log).isTrue();
            assertThat(browser.exitValue()).withFailMessage("Browser suite failed:\n%s", Files.readString(log)).isZero();
        } finally {
            browser.descendants().forEach(ProcessHandle::destroy);
            browser.destroy();
        }
    }
}
