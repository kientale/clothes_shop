package lemonadex.project.clothes;

import lemonadex.project.clothes.features.account.service.AdminAccountService;
import lemonadex.project.clothes.features.auth.dto.*;
import lemonadex.project.clothes.features.auth.service.AuthService;
import lemonadex.project.clothes.common.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminManagementIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired AdminAccountService admins;
    @Autowired PasswordEncoder passwords;

    private static final String PASSWORD = "Password-1234";
    private static final String ADMIN_ACCOUNTS = "/api/v1/admin/admin-accounts";
    private static final String ROLES = "/api/v1/admin/roles";
    private static final String CUSTOMERS = "/api/v1/admin/customers";
    private final List<UUID> createdAccounts = new ArrayList<>();
    private final List<UUID> createdRoles = new ArrayList<>();
    private final List<UUID> createdCustomers = new ArrayList<>();

    @AfterEach
    void removeOnlyTestFixtures() {
        createdCustomers.forEach(id -> jdbc.update("delete from customers where id = ?", id));
        createdAccounts.forEach(id -> {
            jdbc.update("delete from customers where account_id = ?", id);
            jdbc.update("delete from accounts where id = ?", id);
        });
        createdRoles.forEach(id -> jdbc.update("delete from roles where id = ?", id));
    }

    @Test
    void adminAccountCrudHashesPasswordsAndInvalidatesDeletedSessions() throws Exception {
        String token = adminToken();
        Map<String, Object> request = adminCreate(" New Administrator ");
        request.put("email", request.get("email").toString().toUpperCase(Locale.ROOT));
        MvcResult result = perform(post(ADMIN_ACCOUNTS), token, request)
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.fullName").value("New Administrator"))
                .andExpect(jsonPath("$.data.roles", contains("ADMIN")))
                .andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")))
                .andReturn();
        UUID id = track(result, createdAccounts);
        String email = data(result).get("email").toString();
        assertThat(email).isEqualTo(request.get("email").toString().toLowerCase(Locale.ROOT));
        assertThat(passwords.matches(PASSWORD, jdbc.queryForObject("select password_hash from accounts where id = ?", String.class, id))).isTrue();
        perform(get(ADMIN_ACCOUNTS + "/" + id), token, null).andExpect(status().isOk());
        perform(get(ADMIN_ACCOUNTS).param("search", "NEW ADMINISTRATOR").param("size", "1"), token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(id.toString()));

        Map<String, Object> update = adminUpdate(email, "Renamed", "ACTIVE");
        perform(put(ADMIN_ACCOUNTS + "/" + id), token, update)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Renamed"));
        perform(put(ADMIN_ACCOUNTS + "/" + id + "/password"), token, Map.of("password", "Changed-5678"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        assertThat(passwords.matches("Changed-5678", jdbc.queryForObject("select password_hash from accounts where id = ?", String.class, id))).isTrue();
        assertThatThrownBy(() -> auth.login(new LoginRequest(email, PASSWORD))).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        String createdToken = auth.login(new LoginRequest(email, "Changed-5678")).accessToken();

        perform(delete(ADMIN_ACCOUNTS + "/" + id), token, null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select deleted from accounts where id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select deleted from admin_profiles where account_id = ?", Boolean.class, id)).isTrue();
        perform(get(ADMIN_ACCOUNTS + "/" + id), token, null).andExpect(status().isNotFound());
        perform(get(ADMIN_ACCOUNTS).param("search", email), token, null).andExpect(jsonPath("$.data.totalElements").value(0));
        perform(get("/api/v1/auth/me"), createdToken, null).andExpect(status().isUnauthorized());
        perform(post(ADMIN_ACCOUNTS), token, request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void adminStatusTakesEffectOnExistingJwtAndSelfLockoutIsRejected() throws Exception {
        String token = adminToken();
        Map<String, Object> request = adminCreate("Status Test");
        UUID id = track(perform(post(ADMIN_ACCOUNTS), token, request).andExpect(status().isCreated()).andReturn(), createdAccounts);
        String userToken = auth.login(new LoginRequest(request.get("email").toString(), PASSWORD)).accessToken();
        Map<String, Object> update = adminUpdate(request.get("email").toString(), "Status Test", "LOCKED");
        perform(put(ADMIN_ACCOUNTS + "/" + id), token, update).andExpect(status().isOk());
        perform(get("/api/v1/auth/me"), userToken, null).andExpect(status().isUnauthorized());
        update.put("status", "ACTIVE");
        perform(put(ADMIN_ACCOUNTS + "/" + id), token, update).andExpect(status().isOk());
        perform(get("/api/v1/auth/me"), userToken, null).andExpect(status().isOk());

        UUID actor = seededAdminId();
        perform(delete(ADMIN_ACCOUNTS + "/" + actor), token, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ADMIN_MODIFICATION"));
        perform(put(ADMIN_ACCOUNTS + "/" + actor), token, adminUpdate("admin@example.com", "Administrator", "LOCKED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_ADMIN_MODIFICATION"));
    }

    @Test
    void administratorMustRetainAdminRoleAndRequestsRejectMissingRolesAndUnsafeFields() throws Exception {
        String token = adminToken();
        Map<String, Object> request = adminCreate("Invalid Admin");
        request.put("roleIds", List.of(roleId("CUSTOMER")));
        perform(post(ADMIN_ACCOUNTS), token, request).andExpect(status().isBadRequest());
        request.put("roleIds", List.of(UUID.randomUUID()));
        perform(post(ADMIN_ACCOUNTS), token, request).andExpect(status().isNotFound());
        request.put("roleIds", List.of(roleId("ADMIN")));
        request.put("password", "ế".repeat(25));
        perform(post(ADMIN_ACCOUNTS), token, request).andExpect(status().isBadRequest());
        request.put("password", PASSWORD);
        request.put("deleted", true);
        perform(post(ADMIN_ACCOUNTS), token, request).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, request.get("email"))).isZero();
    }

    @Test
    void roleCrudValidatesPermissionsAndPreservesCodesAfterSoftDelete() throws Exception {
        String token = adminToken();
        UUID read = permissionId("CUSTOMER_READ");
        String code = "STAFF_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        Map<String, Object> request = Map.of("name", "  Staff  ", "code", code, "permissionIds", List.of(read));
        MvcResult result = perform(post(ROLES), token, request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Staff"))
                .andExpect(jsonPath("$.data.permissions[0].code").value("CUSTOMER_READ")).andReturn();
        UUID id = track(result, createdRoles);
        perform(get(ROLES + "/" + id), token, null).andExpect(status().isOk());
        perform(get(ROLES).param("search", code).param("size", "1"), token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        perform(put(ROLES + "/" + id), token, Map.of("name", "Read-only staff", "permissionIds", List.of(permissionId("ROLE_READ"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions[0].code").value("ROLE_READ"));
        perform(put(ROLES + "/" + id), token, Map.of("name", "Invalid", "permissionIds", List.of(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        perform(post(ROLES), token, request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROLE_CODE_EXISTS"));
        perform(delete(ROLES + "/" + id), token, null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select deleted from roles where id = ?", Boolean.class, id)).isTrue();
        perform(get(ROLES + "/" + id), token, null).andExpect(status().isNotFound());
        perform(get(ROLES).param("search", code), token, null).andExpect(jsonPath("$.data.totalElements").value(0));
        perform(post(ROLES), token, request).andExpect(status().isConflict());
    }

    @Test
    void systemRolesAndAssignedRolesCannotBeDeletedAndPermissionsAreReadOnlyCatalog() throws Exception {
        String token = adminToken();
        for (String code : List.of("ADMIN", "CUSTOMER")) {
            perform(delete(ROLES + "/" + roleId(code)), token, null).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SYSTEM_ROLE_PROTECTED"));
            perform(put(ROLES + "/" + roleId(code)), token, Map.of("name", "Changed", "permissionIds", List.of()))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SYSTEM_ROLE_PROTECTED"));
        }
        UUID role = newRole(token, List.of());
        Map<String, Object> request = adminCreate("Assigned Role");
        request.put("roleIds", List.of(roleId("ADMIN"), role));
        UUID account = track(perform(post(ADMIN_ACCOUNTS), token, request).andExpect(status().isCreated()).andReturn(), createdAccounts);
        perform(delete(ROLES + "/" + role), token, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROLE_IN_USE"));
        perform(delete(ADMIN_ACCOUNTS + "/" + account), token, null).andExpect(status().isOk());
        perform(delete(ROLES + "/" + role), token, null).andExpect(status().isOk());
        perform(get("/api/v1/admin/permissions").param("module", "role"), token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[*].code", contains("ROLE_READ", "ROLE_WRITE")));
        perform(post("/api/v1/admin/permissions"), token, Map.of()).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void updatingCustomRoleChangesPermissionsOnExistingJwt() throws Exception {
        String token = adminToken();
        UUID role = newRole(token, List.of(permissionId("ROLE_READ")));
        AuthResponse customer = customer();
        jdbc.update("insert into account_roles(account_id, role_id) values (?, ?)", customer.account().id(), role);
        perform(get("/api/v1/auth/me"), customer.accessToken(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions", hasItem("ROLE_READ")));
        perform(put(ROLES + "/" + role), token, Map.of("name", "Changed permissions", "permissionIds", List.of()))
                .andExpect(status().isOk());
        perform(get("/api/v1/auth/me"), customer.accessToken(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions", not(hasItem("ROLE_READ"))));
    }

    @Test
    void customerCrudSupportsGuestsLiteralSearchAndSoftDeletion() throws Exception {
        String token = adminToken();
        Map<String, Object> request = customerRequest(" Guest_100% ", "ACTIVE");
        request.put("phone", "0901234567");
        request.put("dateOfBirth", "2000-01-02");
        UUID id = track(perform(post(CUSTOMERS), token, request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").isEmpty())
                .andExpect(jsonPath("$.data.fullName").value("Guest_100%"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("2000-01-02")).andReturn(), createdCustomers);
        perform(get(CUSTOMERS + "/" + id), token, null).andExpect(status().isOk());
        perform(get(CUSTOMERS).param("search", "guest_100%").param("status", "ACTIVE"), token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        perform(put(CUSTOMERS + "/" + id), token, customerRequest("Updated Guest", "BLOCKED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("BLOCKED"));
        perform(get(CUSTOMERS).param("search", "Updated Guest").param("status", "ACTIVE"), token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        perform(delete(CUSTOMERS + "/" + id), token, null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select deleted from customers where id = ?", Boolean.class, id)).isTrue();
        perform(get(CUSTOMERS + "/" + id), token, null).andExpect(status().isNotFound());
        perform(get(CUSTOMERS).param("search", "Updated Guest"), token, null).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void customerLinksAreValidatedImmutableAndUniqueIncludingDeletedProfiles() throws Exception {
        String token = adminToken();
        AuthResponse customer = customer();
        Map<String, Object> request = customerRequest("Linked", "ACTIVE");
        request.put("accountId", customer.account().id());
        perform(post(CUSTOMERS), token, request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMER_ACCOUNT_ALREADY_LINKED"));
        UUID profile = customer.account().customer().id();
        perform(delete(CUSTOMERS + "/" + profile), token, null).andExpect(status().isOk());
        perform(post(CUSTOMERS), token, request).andExpect(status().isConflict());
        perform(get("/api/v1/auth/me"), customer.accessToken(), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.customer").isEmpty());
        request.put("accountId", seededAdminId());
        perform(post(CUSTOMERS), token, request).andExpect(status().isBadRequest());
        request.put("accountId", UUID.randomUUID());
        perform(post(CUSTOMERS), token, request).andExpect(status().isNotFound());

        UUID guest = track(perform(post(CUSTOMERS), token, customerRequest("Immutable Link", "ACTIVE"))
                .andExpect(status().isCreated()).andReturn(), createdCustomers);
        perform(put(CUSTOMERS + "/" + guest), token, request).andExpect(status().isBadRequest());
    }

    @Test
    void linkedCustomerProfileCanBeCreatedAndReadAfterAccountSoftDeletion() throws Exception {
        String token = adminToken();
        AuthResponse customer = customer();
        jdbc.update("delete from customers where account_id = ?", customer.account().id());
        Map<String, Object> request = customerRequest("Linked profile", "ACTIVE");
        request.put("accountId", customer.account().id());
        UUID id = track(perform(post(CUSTOMERS), token, request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").value(customer.account().id().toString())).andReturn(), createdCustomers);
        jdbc.update("update accounts set deleted = true where id = ?", customer.account().id());
        perform(get(CUSTOMERS + "/" + id), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(customer.account().id().toString()));
    }

    @Test
    void customerEmailSearchSummaryAndLinkableAccountsFollowProfileState() throws Exception {
        String token = adminToken();
        AuthResponse registered = customer();
        String email = registered.account().email();
        UUID accountId = registered.account().id();
        UUID profile = registered.account().customer().id();

        perform(get(CUSTOMERS + "/" + profile), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(email));
        perform(get(CUSTOMERS).param("search", email.substring(0, 13).toUpperCase(Locale.ROOT)), token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[*].email", hasItem(email)));
        track(perform(post(CUSTOMERS), token, customerRequest("Summary guest", "BLOCKED")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(nullValue())).andReturn(), createdCustomers);

        int total = jdbc.queryForObject("select count(*) from customers where not deleted", Integer.class);
        int blocked = jdbc.queryForObject("select count(*) from customers where not deleted and status = 'BLOCKED'", Integer.class);
        perform(get(CUSTOMERS + "/summary"), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(total)).andExpect(jsonPath("$.data.blocked").value(blocked));

        String linkable = CUSTOMERS + "/linkable-accounts";
        // An account that already has a profile, or any administrator, is never offered.
        perform(get(linkable).param("search", email), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
        perform(get(linkable).param("search", "admin@example.com"), token, null).andExpect(jsonPath("$.data", hasSize(0)));
        jdbc.update("delete from customers where account_id = ?", accountId);
        perform(get(linkable).param("search", email), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].id").value(accountId.toString()))
                .andExpect(jsonPath("$.data[0].email").value(email));
        // A soft-deleted profile still holds the account, matching the unique link constraint.
        Map<String, Object> request = customerRequest("Relinked", "ACTIVE");
        request.put("accountId", accountId);
        UUID relinked = track(perform(post(CUSTOMERS), token, request).andExpect(status().isCreated()).andReturn(), createdCustomers);
        perform(delete(CUSTOMERS + "/" + relinked), token, null).andExpect(status().isOk());
        perform(get(linkable).param("search", email), token, null).andExpect(jsonPath("$.data", hasSize(0)));

        perform(get(CUSTOMERS + "/summary"), registered.accessToken(), null).andExpect(status().isForbidden());
        perform(get(linkable), registered.accessToken(), null).andExpect(status().isForbidden());
        perform(get(linkable).param("size", "21"), token, null).andExpect(status().isBadRequest());
    }

    @Test
    void managementRoutesRequireAdminAndWritePermissionIsCheckedAgainstCurrentDatabase() throws Exception {
        AuthResponse customer = customer();
        for (String path : List.of(ADMIN_ACCOUNTS, ROLES, CUSTOMERS, "/api/v1/admin/permissions")) {
            perform(get(path), null, null).andExpect(status().isUnauthorized());
            perform(get(path), customer.accessToken(), null).andExpect(status().isForbidden());
        }
        String token = adminToken();
        try {
            jdbc.update("update permissions set deleted = true where code = 'ACCOUNT_WRITE'");
            perform(get(ADMIN_ACCOUNTS), token, null).andExpect(status().isOk());
            perform(post(ADMIN_ACCOUNTS), token, adminCreate("Denied Write")).andExpect(status().isForbidden());
        } finally {
            jdbc.update("update permissions set deleted = false where code = 'ACCOUNT_WRITE'");
        }
        perform(post(ROLES), customer.accessToken(), Map.of("name", "Denied", "code", "DENIED", "permissionIds", List.of()))
                .andExpect(status().isForbidden());
        perform(post(CUSTOMERS), customer.accessToken(), customerRequest("Denied", "ACTIVE")).andExpect(status().isForbidden());
    }

    @Test
    void managementRejectsInvalidPaginationIdsProfileFieldsAndPasswordPayloads() throws Exception {
        String token = adminToken();
        for (String path : List.of(ADMIN_ACCOUNTS, ROLES, CUSTOMERS)) {
            for (Map<String, String> invalid : List.of(Map.of("page", "-1"), Map.of("size", "51"), Map.of("size", "0"))) {
                var query = get(path);
                invalid.forEach(query::param);
                perform(query, token, null).andExpect(status().isBadRequest());
            }
            perform(get(path + "/bad-uuid"), token, null).andExpect(status().isBadRequest());
            perform(get(path + "/" + UUID.randomUUID()), token, null).andExpect(status().isNotFound());
        }
        Map<String, Object> request = customerRequest("Invalid", "ACTIVE");
        request.put("dateOfBirth", "2999-01-02");
        request.put("phone", "invalid");
        perform(post(CUSTOMERS), token, request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").exists()).andExpect(jsonPath("$.errors.dateOfBirth").exists());
        perform(put(ADMIN_ACCOUNTS + "/" + seededAdminId() + "/password"), token, Map.of("password", "short"))
                .andExpect(status().isBadRequest());
        perform(options(CUSTOMERS + "/" + UUID.randomUUID())
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"), null, null)
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PUT")));
    }

    @Test
    void concurrentDeletesCannotRemoveEveryActiveAdministrator() throws Exception {
        String token = adminToken();
        UUID first = track(perform(post(ADMIN_ACCOUNTS), token, adminCreate("First concurrent admin")).andExpect(status().isCreated()).andReturn(), createdAccounts);
        UUID second = track(perform(post(ADMIN_ACCOUNTS), token, adminCreate("Second concurrent admin")).andExpect(status().isCreated()).andReturn(), createdAccounts);
        List<Map<String, Object>> previous = jdbc.queryForList("select distinct a.id, a.status from accounts a join account_roles ar on a.id = ar.account_id join roles r on r.id = ar.role_id where r.code = 'ADMIN' and not a.deleted and a.id not in (?, ?)", first, second);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            previous.forEach(row -> jdbc.update("update accounts set status = 'INACTIVE' where id = ?", row.get("id")));
            Callable<Boolean> removeFirst = () -> { start.await(); return attemptDelete(first, second); };
            Callable<Boolean> removeSecond = () -> { start.await(); return attemptDelete(second, first); };
            Future<Boolean> one = executor.submit(removeFirst);
            Future<Boolean> two = executor.submit(removeSecond);
            start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("select count(*) from accounts where id in (?, ?) and not deleted and status = 'ACTIVE'", Integer.class, first, second)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            previous.forEach(row -> jdbc.update("update accounts set status = ? where id = ?", row.get("status"), row.get("id")));
        }
    }

    private boolean attemptDelete(UUID id, UUID actorId) {
        try {
            admins.delete(id, actorId);
            return true;
        } catch (ConflictException ex) {
            assertThat(ex.getCode()).isEqualTo("LAST_ACTIVE_ADMIN");
            return false;
        }
    }

    private String adminToken() {
        return auth.login(new LoginRequest("admin", "admin123")).accessToken();
    }

    private AuthResponse customer() {
        AuthResponse response = auth.register(new RegisterRequest(UUID.randomUUID() + "@example.com", PASSWORD, PASSWORD, "Fixture Customer", null, null));
        createdAccounts.add(response.account().id());
        return response;
    }

    private UUID seededAdminId() {
        return jdbc.queryForObject("select id from accounts where email = 'admin@example.com'", UUID.class);
    }

    private UUID roleId(String code) {
        return jdbc.queryForObject("select id from roles where code = ?", UUID.class, code);
    }

    private UUID permissionId(String code) {
        return jdbc.queryForObject("select id from permissions where code = ?", UUID.class, code);
    }

    private Map<String, Object> adminCreate(String name) {
        Map<String, Object> request = adminUpdate(UUID.randomUUID() + "@example.com", name, "ACTIVE");
        request.remove("status");
        request.put("password", PASSWORD);
        return request;
    }

    private Map<String, Object> adminUpdate(String email, String name, String state) {
        return new LinkedHashMap<>(Map.of("email", email, "fullName", name, "status", state, "roleIds", List.of(roleId("ADMIN"))));
    }

    private Map<String, Object> customerRequest(String name, String state) {
        return new LinkedHashMap<>(Map.of("fullName", name, "status", state));
    }

    private UUID newRole(String token, List<UUID> permissions) throws Exception {
        return track(perform(post(ROLES), token, Map.of("name", "Custom role", "code", "R_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT), "permissionIds", permissions))
                .andExpect(status().isCreated()).andReturn(), createdRoles);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }

    private Map<?, ?> data(MvcResult result) throws Exception {
        return (Map<?, ?>) json.readValue(result.getResponse().getContentAsString(), Map.class).get("data");
    }

    private UUID track(MvcResult result, List<UUID> list) throws Exception {
        UUID id = UUID.fromString(data(result).get("id").toString());
        list.add(id);
        return id;
    }
}
