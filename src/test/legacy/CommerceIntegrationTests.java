package lemonadex.project.clothes;

import lemonadex.project.clothes.service.CartService;
import lemonadex.project.clothes.service.CatalogService;
import lemonadex.project.clothes.dto.catalog.*;
import lemonadex.project.clothes.service.AuthService;
import lemonadex.project.clothes.dto.auth.*;
import lemonadex.project.clothes.service.InventoryService;
import lemonadex.project.clothes.service.OrderService;
import lemonadex.project.clothes.dto.order.*;
import lemonadex.project.clothes.model.*;
import lemonadex.project.clothes.repository.OrderRepository;
import lemonadex.project.clothes.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import lemonadex.project.clothes.config.SecurityProperties;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CommerceIntegrationTests {
    @Autowired AuthService auth;
    @Autowired CatalogService catalog;
    @Autowired CartService carts;
    @Autowired InventoryService inventory;
    @Autowired OrderService orders;
    @Autowired OrderRepository orderRepository;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired SecurityProperties security;

    private static final String PASSWORD = "Password-1234";

    @Test
    void openApiDocumentsEveryCommerceRoute() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = json.readValue(Files.readString(Path.of("docs/openapi.json")), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) spec.get("paths");
        Set<String> documented = new HashSet<>();
        paths.forEach((path, operations) -> operations.keySet().forEach(method ->
                documented.add(method.toUpperCase(Locale.ROOT) + " " + path)));
        Set<String> implemented = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (handler.getBeanType().getName().startsWith("lemonadex.project.clothes.")) {
                mapping.getPatternValues().forEach(path -> mapping.getMethodsCondition().getMethods()
                        .forEach(method -> implemented.add(method.name() + " " + path)));
            }
        });
        assertThat(documented).containsExactlyInAnyOrderElementsOf(implemented);
    }

    @Test
    void signedTokensRequireValidIssuerAudienceExpiryAndIdentity() throws Exception {
        Instant now = Instant.now();
        List<JwtClaimsSet> invalid = List.of(
                claims(security.issuer(), security.audience(), UUID.randomUUID().toString(), now.minusSeconds(120)),
                claims("wrong-issuer", security.audience(), UUID.randomUUID().toString(), now.plusSeconds(300)),
                claims(security.issuer(), "wrong-audience", UUID.randomUUID().toString(), now.plusSeconds(300)),
                claims(security.issuer(), security.audience(), "invalid-user-id", now.plusSeconds(300)),
                JwtClaimsSet.builder().issuer(security.issuer()).subject(UUID.randomUUID().toString())
                        .audience(List.of(security.audience())).claim("roles", List.of("CUSTOMER")).build());
        for (JwtClaimsSet claims : invalid) {
            String token = jwtEncoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void httpWorkflowCreatesProductAndChecksOutFromCart() throws Exception {
        TokenResponse adminFixture = customer();
        jdbc.update("update users set role = 'ADMIN' where id = ?", adminFixture.user().id());
        String loginBody = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(adminFixture.user().email(), PASSWORD))))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString();
        String adminToken = "Bearer " + json.readTree(loginBody).get("accessToken").asText();
        String suffix = UUID.randomUUID().toString();
        String categoryBody = mvc.perform(post("/api/v1/admin/categories").header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CategoryRequest("Shirts", "shirts-" + suffix))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID category = UUID.fromString(json.readTree(categoryBody).get("id").asText());
        ProductRequest product = new ProductRequest("HTTP Shirt", "http-shirt-" + suffix, null, "LemonadeX", category,
                List.of(), List.of(new VariantRequest("HTTP-" + suffix, "S", "White", new BigDecimal("120000.00"), 4)));
        String productBody = mvc.perform(post("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(product)))
                .andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.variants[0].availableQuantity").value(4))
                .andReturn().getResponse().getContentAsString();
        UUID variant = UUID.fromString(json.readTree(productBody).get("variants").get(0).get("id").asText());
        TokenResponse buyer = customer();
        mvc.perform(put("/api/v1/cart/items/" + variant).header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(240000));
        String key = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(checkout())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(240000))
                .andExpect(jsonPath("$.status").value("PLACED")).andExpect(header().exists(HttpHeaders.LOCATION));
        assertThat(stock(variant)).isEqualTo(2);
    }

    private JwtClaimsSet claims(String issuer, String audience, String subject, Instant expiry) {
        return JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(subject)
                .issuedAt(Instant.now().minusSeconds(300)).expiresAt(expiry)
                .claim("roles", List.of("CUSTOMER")).build();
    }

    @Test
    void registrationNormalizesEmailAndNeverGrantsAdmin() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        TokenResponse token = auth.register(new RegisterRequest(email.toUpperCase(Locale.ROOT), PASSWORD, "Customer"));
        assertThat(token.user().email()).isEqualTo(email);
        assertThat(token.user().role()).isEqualTo("CUSTOMER");
        assertThat(carts.get(token.user().id()).items()).isEmpty();
        String hash = jdbc.queryForObject("select password_hash from users where id = ?", String.class, token.user().id());
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"evil@example.com\",\"password\":\"Password-1234\",\"displayName\":\"Evil\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginRejectsWrongCredentialsAndDuplicateEmail() {
        TokenResponse token = customer();
        assertThat(auth.login(new LoginRequest(token.user().email(), PASSWORD)).user().id()).isEqualTo(token.user().id());
        assertThatThrownBy(() -> auth.login(new LoginRequest(token.user().email(), "Wrong-password")))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INVALID_CREDENTIALS");
        assertThatThrownBy(() -> auth.register(new RegisterRequest(token.user().email(), PASSWORD, "Duplicate")))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("EMAIL_EXISTS");
    }

    @Test
    void multibytePasswordsCannotExceedBcryptLimit() {
        assertThatThrownBy(() -> auth.register(new RegisterRequest(UUID.randomUUID() + "@example.com",
                "密".repeat(30), "Customer"))).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void checkoutSnapshotsPriceAndCancellationRestoresStockOnce() {
        TokenResponse customer = customer();
        ProductResponse product = product(5);
        UUID variant = product.variants().getFirst().id();
        carts.put(customer.user().id(), variant, 2);
        OrderResponse order = orders.checkout(customer.user().id(), UUID.randomUUID(), checkout());
        assertThat(order.total()).isEqualByComparingTo("500000.00");
        assertThat(order.items().getFirst().size()).isEqualTo("M");
        assertThat(stock(variant)).isEqualTo(3);
        assertThat(carts.get(customer.user().id()).items()).isEmpty();
        catalog.changePrice(product.id(), variant, new BigDecimal("300000.00"));
        assertThat(orders.get(customer.user().id(), order.id()).items().getFirst().unitPrice())
                .isEqualByComparingTo("250000.00");
        orders.cancel(customer.user().id(), order.id());
        orders.cancel(customer.user().id(), order.id());
        assertThat(stock(variant)).isEqualTo(5);
        assertThat(orders.get(customer.user().id(), order.id()).paymentStatus()).isEqualTo(PaymentStatus.VOID);
    }

    @Test
    void insufficientStockRollsBackEveryMutation() {
        UUID userId = customer().user().id();
        ProductResponse product = product(1, 1);
        List<UUID> variants = product.variants().stream().map(VariantResponse::id).sorted().toList();
        // The first locked item succeeds; the second must fail and roll the first one back.
        carts.put(userId, variants.get(0), 1);
        carts.put(userId, variants.get(1), 2);
        assertThatThrownBy(() -> orders.checkout(userId, UUID.randomUUID(), checkout()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INSUFFICIENT_STOCK");
        assertThat(stock(variants.get(0))).isEqualTo(1);
        assertThat(stock(variants.get(1))).isEqualTo(1);
        assertThat(carts.get(userId).items()).hasSize(2);
        assertThat(orders.list(userId, 0, 20).getTotalElements()).isZero();
        Integer movementCount = jdbc.queryForObject(
                "select count(*) from stock_movements where variant_id in (?, ?) and reason = 'ORDER_PLACED'",
                Integer.class, variants.get(0), variants.get(1));
        assertThat(movementCount).isZero();
    }

    @Test
    void checkoutKeyReplaysExistingOrderAndRejectsDifferentPayload() {
        UUID userId = customer().user().id();
        UUID variant = product(3).variants().getFirst().id();
        carts.put(userId, variant, 1);
        UUID key = UUID.randomUUID();
        OrderResponse first = orders.checkout(userId, key, checkout());
        OrderResponse replay = orders.checkout(userId, key, checkout());
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(orders.list(userId, 0, 20).getTotalElements()).isEqualTo(1);
        CheckoutRequest changed = new CheckoutRequest(checkout().shippingAddress(), PaymentMethod.COD, "Changed");
        assertThatThrownBy(() -> orders.checkout(userId, key, changed)).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void customersCannotReadOrCancelOtherCustomersOrders() throws Exception {
        TokenResponse owner = customer();
        TokenResponse stranger = customer();
        UUID variant = product(3).variants().getFirst().id();
        carts.put(owner.user().id(), variant, 1);
        UUID id = orders.checkout(owner.user().id(), UUID.randomUUID(), checkout()).id();
        mvc.perform(get("/api/v1/orders/" + id).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/orders/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(orders.get(owner.user().id(), id).status()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void publicCatalogIsAccessibleAndAdminRequiresRole() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED")).andExpect(header().exists("X-Request-Id"));
        TokenResponse customer = customer();
        mvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // Promote a test fixture through the database; there is no public role assignment endpoint.
        jdbc.update("update users set role = 'ADMIN' where id = ?", customer.user().id());
        TokenResponse admin = auth.login(new LoginRequest(customer.user().email(), PASSWORD));
        mvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void malformedAndTamperedTokensAreRejected() throws Exception {
        TokenResponse token = customer();
        String[] parts = token.accessToken().split("\\.");
        String altered = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + altered))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validationReturnsProblemDetailsAndBoundsPagination() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"password\":\"short\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists()).andExpect(jsonPath("$.requestId").exists());
        mvc.perform(get("/api/v1/products").param("size", "1000"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("page", "-1"))
                .andExpect(status().isBadRequest());
        TokenResponse customer = customer();
        mvc.perform(put("/api/v1/cart/items/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(checkout())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inactiveProductsAreHiddenAndCannotBePurchased() {
        UUID userId = customer().user().id();
        ProductResponse product = product(3);
        UUID variant = product.variants().getFirst().id();
        carts.put(userId, variant, 1);
        catalog.changeActive(product.id(), false);
        assertThatThrownBy(() -> catalog.product(product.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> orders.checkout(userId, UUID.randomUUID(), checkout()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("PRODUCT_UNAVAILABLE");
        assertThat(stock(variant)).isEqualTo(3);
        carts.remove(userId, variant);
        assertThat(carts.get(userId).items()).isEmpty();
    }

    @Test
    void orderTransitionsFollowFulfillmentAndCodPayment() {
        UUID userId = customer().user().id();
        UUID variant = product(2).variants().getFirst().id();
        carts.put(userId, variant, 1);
        UUID id = orders.checkout(userId, UUID.randomUUID(), checkout()).id();
        assertThatThrownBy(() -> orders.changeStatus(id, OrderStatus.DELIVERED))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INVALID_ORDER_TRANSITION");
        orders.changeStatus(id, OrderStatus.CONFIRMED);
        orders.changeStatus(id, OrderStatus.SHIPPED);
        assertThatThrownBy(() -> orders.cancel(userId, id)).isInstanceOf(BusinessException.class);
        OrderResponse delivered = orders.changeStatus(id, OrderStatus.DELIVERED);
        assertThat(delivered.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(stock(variant)).isEqualTo(1);
    }

    @Test
    void concurrentBuyersCannotOversell() throws Exception {
        UUID variant = product(1).variants().getFirst().id();
        UUID first = customer().user().id();
        UUID second = customer().user().id();
        carts.put(first, variant, 1);
        carts.put(second, variant, 1);
        List<Boolean> results = race(() -> attemptCheckout(first), () -> attemptCheckout(second));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(stock(variant)).isZero();
        assertThat(orders.list(first, 0, 20).getTotalElements() + orders.list(second, 0, 20).getTotalElements()).isEqualTo(1);
    }

    @Test
    void concurrentRetriesCreateOnlyOneOrder() throws Exception {
        UUID userId = customer().user().id();
        UUID variant = product(3).variants().getFirst().id();
        carts.put(userId, variant, 1);
        UUID key = UUID.randomUUID();
        List<UUID> ids = race(() -> orders.checkout(userId, key, checkout()).id(),
                () -> orders.checkout(userId, key, checkout()).id());
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(orders.list(userId, 0, 20).getTotalElements()).isEqualTo(1);
    }

    @Test
    void stockAdjustmentsCannotMakeQuantityNegative() {
        UUID variant = product(2).variants().getFirst().id();
        assertThatThrownBy(() -> inventory.adjust(variant, -3, "Correction"))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INSUFFICIENT_STOCK");
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(inventory.adjust(variant, 5, "Restock")).isEqualTo(7);
    }

    private TokenResponse customer() {
        return auth.register(new RegisterRequest(UUID.randomUUID() + "@example.com", PASSWORD, "Customer"));
    }

    private ProductResponse product(int... quantities) {
        String suffix = UUID.randomUUID().toString();
        UUID category = catalog.createCategory(new CategoryRequest("Clothing", "clothing-" + suffix)).id();
        List<VariantRequest> variants = new ArrayList<>();
        for (int index = 0; index < quantities.length; index++) {
            variants.add(new VariantRequest("SKU-" + suffix + "-" + index, index == 0 ? "M" : "L",
                    "Black", new BigDecimal("250000.00"), quantities[index]));
        }
        return catalog.createProduct(new ProductRequest("Cotton Shirt", "shirt-" + suffix, "Cotton", "LemonadeX",
                category, List.of("https://example.com/shirt.jpg"), variants));
    }

    private CheckoutRequest checkout() {
        return new CheckoutRequest(new ShippingRequest("Customer", "0901234567", "12 Nguyen Hue",
                "Ho Chi Minh", "District 1", "Ben Nghe"), PaymentMethod.COD, null);
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.accessToken();
    }

    private int stock(UUID variant) {
        return inventory.available(Set.of(variant)).get(variant);
    }

    private boolean attemptCheckout(UUID userId) {
        try {
            orders.checkout(userId, UUID.randomUUID(), checkout());
            return true;
        } catch (BusinessException ex) {
            if (!ex.getCode().equals("INSUFFICIENT_STOCK")) {
                throw ex;
            }
            return false;
        }
    }

    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<T> one = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return first.call();
            });
            Future<T> two = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return second.call();
            });
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        }
    }
}
