package lemonadex.project.clothes;

import lemonadex.project.clothes.dto.auth.*;
import lemonadex.project.clothes.model.Account;
import lemonadex.project.clothes.properties.SecurityProperties;
import lemonadex.project.clothes.repository.AccountRepository;
import lemonadex.project.clothes.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired SecurityProperties security;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    private static final String PASSWORD = "Password-1234";

    @Test
    void seededAdminCanLoginUsingRequestedAliasAndReadAdminAccounts() throws Exception {
        for (String identifier : List.of("admin", "ADMIN", "admin@example.com")) {
            MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", identifier, "password", "admin123"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("LOGIN_SUCCESS"))
                    .andExpect(jsonPath("$.data.account.email").value("admin@example.com"))
                    .andExpect(jsonPath("$.data.account.roles", contains("ADMIN")))
                    .andExpect(jsonPath("$.data.account.permissions", contains("ACCOUNT_READ", "AUTH_PROFILE_READ")))
                    .andReturn();
            Map<?, ?> data = (Map<?, ?>) body(result).get("data");
            mvc.perform(get("/api/v1/admin/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + data.get("accessToken")))
                    .andExpect(status().isOk());
        }
        String hash = jdbc.queryForObject("select password_hash from accounts where email = 'admin@example.com'", String.class);
        assertThat(hash).startsWith("$2").isNotEqualTo("admin123");
        assertThat(passwords.matches("admin123", hash)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from admin_profiles p join accounts a on p.account_id = a.id where a.email = 'admin@example.com'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void adminSeedCanBeReappliedWithoutDuplicatingOrResettingAccount() throws Exception {
        UUID originalId = jdbc.queryForObject("select id from accounts where email = 'admin@example.com'", UUID.class);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V3__seed_admin_account.sql")));
        assertThat(jdbc.queryForObject("select id from accounts where email = 'admin@example.com'", UUID.class)).isEqualTo(originalId);
        assertThat(jdbc.queryForObject("select count(*) from account_roles where account_id = ?", Integer.class, originalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from admin_profiles where account_id = ?", Integer.class, originalId)).isEqualTo(1);
    }

    @Test
    void loginStillRejectsOtherNonEmailIdentifiers() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "other-username", "password", PASSWORD))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "admin", "password", "incorrect-password"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void registrationCreatesCustomerAndReturnsSafeJwtResponse() throws Exception {
        String email = email().toUpperCase(Locale.ROOT);
        Map<String, Object> request = registration(email);
        request.put("fullName", "  Nguyen An  ");
        request.put("phone", "0901234567");
        request.put("dateOfBirth", "2000-01-02");
        MvcResult result = register(request)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("REGISTER_SUCCESS"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.account.email").value(email.toLowerCase(Locale.ROOT)))
                .andExpect(jsonPath("$.data.account.roles", contains("CUSTOMER")))
                .andExpect(jsonPath("$.data.account.permissions", contains("AUTH_PROFILE_READ")))
                .andExpect(jsonPath("$.data.account.customer.fullName").value("Nguyen An"))
                .andExpect(jsonPath("$.data.account.customer.dateOfBirth").value("2000-01-02"))
                .andExpect(jsonPath("$.data.account.createdAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")))
                .andExpect(jsonPath("$.data.account.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.account.password").doesNotExist())
                .andExpect(jsonPath("$.data.account.deleted").doesNotExist())
                .andExpect(jsonPath("$.requestId").value(not(emptyString())))
                .andReturn();
        Map<?, ?> data = (Map<?, ?>) body(result).get("data");
        Map<?, ?> account = (Map<?, ?>) data.get("account");
        UUID id = UUID.fromString((String) account.get("id"));
        String hash = jdbc.queryForObject("select password_hash from accounts where id = ?", String.class, id);
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from customers where account_id = ? and not deleted", Integer.class, id))
                .isEqualTo(1);
        assertThat(body(result).get("requestId")).isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + data.get("accessToken")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void invalidFieldsAndPasswordConfirmationReturnValidationErrors() throws Exception {
        Map<String, Object> request = registration("invalid-email");
        request.put("password", "short");
        request.put("fullName", " ");
        request.put("phone", "abc");
        request.put("dateOfBirth", LocalDate.now().plusDays(1).toString());
        register(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.phone").exists())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists())
                .andExpect(jsonPath("$.errors.passwordConfirmed").exists());
    }

    @Test
    void bcryptByteLimitRejectsLongMultibytePassword() throws Exception {
        Map<String, Object> request = registration(email());
        String password = "á".repeat(37);
        request.put("password", password);
        request.put("confirmPassword", password);
        register(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"roles", "status", "id", "deleted"})
    void registrationRejectsClientControlledIdentityAndAuthorities(String field) throws Exception {
        Map<String, Object> request = registration(email());
        request.put(field, field.equals("roles") ? List.of("ADMIN") : "injected");
        register(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void duplicateEmailIsCaseInsensitiveAndSoftDeletedEmailRemainsReserved() throws Exception {
        AuthResponse customer = customer();
        register(registration(customer.account().email().toUpperCase(Locale.ROOT)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        jdbc.update("update accounts set deleted = true where id = ?", customer.account().id());
        register(registration(customer.account().email()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        assertThat(jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, customer.account().email()))
                .isEqualTo(1);
    }

    @Test
    void concurrentRegistrationsCreateOnlyOneAccountAndCustomer() throws Exception {
        String email = email();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> request = () -> {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
            return register(registration(email)).andReturn().getResponse().getStatus();
        };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(request);
            Future<Integer> second = executor.submit(request);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from customers c join accounts a on a.id = c.account_id where a.email = ?",
                Integer.class, email)).isEqualTo(1);
    }

    @Test
    void loginUpdatesLastLoginAndReturnsBearerToken() throws Exception {
        AuthResponse customer = customer();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", customer.account().email().toUpperCase(Locale.ROOT), "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.account.lastLoginAt").isNotEmpty())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        assertThat(jdbc.queryForObject("select last_login_at is not null from accounts where id = ?", Boolean.class, customer.account().id()))
                .isTrue();
    }

    @Test
    void wrongPasswordAndUnknownEmailHaveSamePublicError() throws Exception {
        AuthResponse customer = customer();
        for (String email : List.of(customer.account().email(), email())) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", email, "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }
        assertThat(jdbc.queryForObject("select last_login_at from accounts where id = ?", Object.class, customer.account().id())).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "LOCKED", "SUSPENDED"})
    void inactiveAccountCannotLoginOrReuseExistingJwt(String status) throws Exception {
        AuthResponse customer = customer();
        jdbc.update("update accounts set status = ? where id = ?", status, customer.account().id());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", customer.account().email(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void repositoryDeletionIsSoftAndInvalidatesExistingJwt() throws Exception {
        AuthResponse customer = customer();
        UUID id = customer.account().id();
        accounts.deleteById(id);
        assertThat(jdbc.queryForObject("select deleted from accounts where id = ?", Boolean.class, id)).isTrue();
        assertThat(accounts.findById(id)).isEmpty();
        assertThat(accounts.findByIdAndDeletedFalse(id)).isEmpty();
        assertThat(accounts.existsByEmailIgnoreCaseAndDeletedFalse(customer.account().email())).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from customers where account_id = ?", Integer.class, id)).isEqualTo(1);
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", customer.account().email(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingMalformedAndTamperedTokensUseApiResponse() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        String token = customer().accessToken();
        String[] parts = token.split("\\.");
        String signature = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        for (String invalid : List.of("invalid", parts[0] + "." + parts[1] + "." + signature)) {
            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + invalid))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void signedTokensMustHaveValidExpiryIssuerAudienceAndSubject() throws Exception {
        UUID id = customer().account().id();
        Instant now = Instant.now();
        List<JwtClaimsSet> invalid = List.of(
                claims(id.toString(), security.issuer(), security.audience(), now.minusSeconds(300), now.minusSeconds(120)),
                claims(id.toString(), "wrong-issuer", security.audience(), now, now.plusSeconds(900)),
                claims(id.toString(), security.issuer(), "wrong-audience", now, now.plusSeconds(900)),
                claims("invalid-uuid", security.issuer(), security.audience(), now, now.plusSeconds(900)),
                claims(UUID.randomUUID().toString(), security.issuer(), security.audience(), now, now.plusSeconds(900)),
                JwtClaimsSet.builder().subject(id.toString()).issuer(security.issuer()).audience(List.of(security.audience())).issuedAt(now).build());
        for (JwtClaimsSet claim : invalid) {
            String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claim)).getTokenValue();
            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void customerCannotReadAdminAccountsAndDbGrantTakesEffectOnExistingToken() throws Exception {
        AuthResponse customer = customer();
        mvc.perform(get("/api/v1/admin/accounts").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        grantAdmin(customer.account().id());
        mvc.perform(get("/api/v1/admin/accounts").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isArray());
        jdbc.update("delete from account_roles where account_id = ? and role_id = (select id from roles where code = 'ADMIN')", customer.account().id());
        mvc.perform(get("/api/v1/admin/accounts").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedPermissionAndRoleAreIgnoredDuringAuthorization() throws Exception {
        AuthResponse customer = customer();
        try {
            jdbc.update("update permissions set deleted = true where code = 'AUTH_PROFILE_READ'");
            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("update permissions set deleted = false where code = 'AUTH_PROFILE_READ'");
        }
        try {
            jdbc.update("update roles set deleted = true where code = 'CUSTOMER'");
            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                    .andExpect(status().isUnauthorized());
        } finally {
            jdbc.update("update roles set deleted = false where code = 'CUSTOMER'");
        }
    }

    @Test
    void adminListUsesPageResponseAndFiltersSoftDeletedRows() throws Exception {
        AuthResponse admin = customer();
        grantAdmin(admin.account().id());
        AuthResponse deleted = customer();
        jdbc.update("update accounts set deleted = true where id = ?", deleted.account().id());
        mvc.perform(get("/api/v1/admin/accounts").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("search", deleted.account().email()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/admin/accounts").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("search", admin.account().email().toUpperCase(Locale.ROOT)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(admin.account().id().toString()))
                .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/admin/accounts/" + deleted.account().id()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidPaginationAndQueryTypesReturnUniformBadRequest() throws Exception {
        AuthResponse admin = customer();
        grantAdmin(admin.account().id());
        for (Map<String, String> invalid : List.of(Map.of("size", "51"), Map.of("size", "0"), Map.of("page", "-1"), Map.of("status", "UNKNOWN"))) {
            var request = get("/api/v1/admin/accounts").header(HttpHeaders.AUTHORIZATION, bearer(admin));
            invalid.forEach(request::param);
            mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(get("/api/v1/admin/accounts/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void allowedCorsPreflightAndMalformedJsonAreHandled() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("broken"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void openApiDocumentsAllImplementedApiRoutes() throws Exception {
        Map<?, ?> spec = json.readValue(Files.readString(Path.of("docs/openapi.json")), Map.class);
        Map<?, ?> paths = (Map<?, ?>) spec.get("paths");
        Set<String> documented = new HashSet<>();
        paths.forEach((path, operations) -> ((Map<?, ?>) operations).keySet().forEach(method ->
                documented.add(method.toString().toUpperCase(Locale.ROOT) + " " + path)));
        Set<String> implemented = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (handler.getBeanType().getPackageName().equals("lemonadex.project.clothes.controller")) {
                mapping.getPatternValues().forEach(path -> mapping.getMethodsCondition().getMethods()
                        .forEach(method -> implemented.add(method.name() + " " + path)));
            }
        });
        assertThat(documented).containsExactlyInAnyOrderElementsOf(implemented);
    }

    private AuthResponse customer() {
        return auth.register(new RegisterRequest(email(), PASSWORD, PASSWORD, "Test Customer", null, null));
    }

    private String email() {
        return UUID.randomUUID() + "@example.com";
    }

    private Map<String, Object> registration(String email) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("email", email);
        request.put("password", PASSWORD);
        request.put("confirmPassword", PASSWORD);
        request.put("fullName", "Test Customer");
        return request;
    }

    private org.springframework.test.web.servlet.ResultActions register(Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
    }

    private String bearer(AuthResponse response) {
        return "Bearer " + response.accessToken();
    }

    private void grantAdmin(UUID accountId) {
        jdbc.update("insert into account_roles(account_id, role_id) select ?, id from roles where code = 'ADMIN' on conflict do nothing", accountId);
    }

    private Map<?, ?> body(MvcResult result) throws Exception {
        return json.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private JwtClaimsSet claims(String subject, String issuer, String audience, Instant issued, Instant expires) {
        return JwtClaimsSet.builder().subject(subject).issuer(issuer).audience(List.of(audience))
                .issuedAt(issued).expiresAt(expires).build();
    }
}
