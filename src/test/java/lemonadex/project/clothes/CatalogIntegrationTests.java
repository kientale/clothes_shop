package lemonadex.project.clothes;

import lemonadex.project.clothes.features.auth.dto.*;
import lemonadex.project.clothes.features.auth.service.AuthService;
import lemonadex.project.clothes.features.catalog.repository.CatalogWriteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
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
class CatalogIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired CatalogWriteRepository writes;
    @Autowired PlatformTransactionManager transactions;

    private static final String API = "/api/v1/admin";
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private String token;

    @AfterEach
    void removeFixtures() {
        // Fixture names all contain the per-test tag; delete children before parents.
        String like = "%" + tag + "%";
        jdbc.update("delete from collection_products where collection_id in (select id from collections where name like ?)", like);
        jdbc.update("delete from collections where name like ?", like);
        jdbc.update("delete from product_images where product_id in (select id from products where name like ?)", like);
        jdbc.update("delete from product_variants where product_id in (select id from products where name like ?)", like);
        jdbc.update("delete from products where name like ?", like);
        jdbc.update("delete from sizes where name like ?", like);
        jdbc.update("delete from colors where name like ?", like);
        jdbc.update("delete from brands where name like ?", like);
        jdbc.update("delete from categories where parent_id is not null and name like ?", like);
        jdbc.update("delete from categories where name like ?", like);
    }

    @Test
    void taxonomyCrudNormalisesNamesAndProtectsItemsInUse() throws Exception {
        token = adminToken();
        UUID root = id(create("/categories", Map.of("name", "Thời trang Nữ " + tag, "status", "ACTIVE"))
                .andExpect(jsonPath("$.data.slug").value("thoi-trang-nu-" + tag)));
        UUID child = id(create("/categories", map("parentId", root, "name", "Áo đầm " + tag, "slug", "", "status", "ACTIVE"))
                .andExpect(jsonPath("$.data.parentName").value("Thời trang Nữ " + tag)));
        perform(put(API + "/categories/" + root), map("parentId", child, "name", "Thời trang Nữ " + tag, "status", "ACTIVE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));
        perform(get(API + "/categories/" + root), null).andExpect(jsonPath("$.data.childCount").value(1));
        perform(delete(API + "/categories/" + root), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_HAS_CHILDREN"));
        create("/categories", Map.of("name", "Trùng " + tag, "slug", "thoi-trang-nu-" + tag, "status", "ACTIVE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLUG_EXISTS"));

        UUID color = id(create("/colors", Map.of("name", "Đỏ " + tag, "code", "red-" + tag, "hexCode", "#ff0000", "status", "ACTIVE"))
                .andExpect(jsonPath("$.data.code").value(("RED-" + tag).toUpperCase(Locale.ROOT)))
                .andExpect(jsonPath("$.data.hexCode").value("#FF0000")));
        create("/colors", Map.of("name", "Đỏ 2 " + tag, "code", "RED-" + tag, "status", "ACTIVE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CODE_EXISTS"));
        UUID size = id(create("/sizes", Map.of("name", "M " + tag, "code", "M-" + tag, "sortOrder", 2, "status", "ACTIVE")));
        UUID brand = id(create("/brands", Map.of("name", "Lemon Studio " + tag, "status", "ACTIVE")));
        UUID product = id(create("/products", product("P-" + tag, brand, child, List.of())));
        create("/product-variants", map("productId", product, "colorId", color, "sizeId", size, "price", 199000, "status", "ACTIVE"));

        perform(delete(API + "/brands/" + brand), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRAND_IN_USE"));
        perform(delete(API + "/categories/" + child), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        perform(delete(API + "/colors/" + color), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COLOR_IN_USE"));
        perform(delete(API + "/sizes/" + size), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SIZE_IN_USE"));
        perform(get(API + "/brands").param("search", "lemon studio " + tag), null)
                .andExpect(jsonPath("$.data.content[0].productCount").value(1));
        perform(get(API + "/colors").param("search", tag), null).andExpect(jsonPath("$.data.content[0].variantCount").value(1));

        // Deleting the product also removes its variants, which frees everything it used.
        perform(delete(API + "/products/" + product), null).andExpect(status().isOk());
        perform(get(API + "/product-variants").param("productId", product.toString()), null).andExpect(jsonPath("$.data.totalElements").value(0));
        for (String path : List.of("/colors/" + color, "/sizes/" + size, "/brands/" + brand, "/categories/" + child, "/categories/" + root)) {
            perform(delete(API + path), null).andExpect(status().isOk());
            perform(get(API + path), null).andExpect(status().isNotFound());
        }
    }

    @Test
    void productsKeepOrderedImagesAndVariantsSupportBulkCreationAndRestore() throws Exception {
        token = adminToken();
        UUID category = id(create("/categories", Map.of("name", "Áo " + tag, "status", "ACTIVE")));
        UUID brand = id(create("/brands", Map.of("name", "Brand " + tag, "status", "ACTIVE")));
        UUID red = id(create("/colors", Map.of("name", "Red " + tag, "code", "R" + tag, "status", "ACTIVE")));
        UUID blue = id(create("/colors", Map.of("name", "Blue " + tag, "code", "B" + tag, "status", "ACTIVE")));
        UUID s = id(create("/sizes", Map.of("name", "S " + tag, "code", "S" + tag, "sortOrder", 1, "status", "ACTIVE")));
        UUID m = id(create("/sizes", Map.of("name", "M " + tag, "code", "M" + tag, "sortOrder", 2, "status", "ACTIVE")));

        List<Map<String, Object>> images = List.of(Map.of("url", "https://img.example.com/1.jpg"), Map.of("url", "https://img.example.com/2.jpg", "altText", "Back"));
        UUID product = id(create("/products", product("tee-" + tag, brand, category, images))
                .andExpect(jsonPath("$.data.productCode").value(("TEE-" + tag).toUpperCase(Locale.ROOT)))
                .andExpect(jsonPath("$.data.slug").value("ao-thun-" + tag))
                .andExpect(jsonPath("$.data.images[0].primary").value(true))
                .andExpect(jsonPath("$.data.images[1].altText").value("Back")));
        // Reordering swaps the primary image, which must not trip the one-primary-image index.
        perform(put(API + "/products/" + product), product("tee-" + tag, brand, category, List.of(images.get(1), images.get(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images[0].url").value("https://img.example.com/2.jpg"))
                .andExpect(jsonPath("$.data.images[0].primary").value(true))
                .andExpect(jsonPath("$.data.images[1].primary").value(false));
        assertThat(jdbc.queryForObject("select count(*) from product_images where product_id = ?", Integer.class, product)).isEqualTo(2);

        UUID first = id(create("/product-variants", map("productId", product, "colorId", red, "sizeId", s, "price", 150000, "status", "ACTIVE"))
                .andExpect(jsonPath("$.data.sku").value(("TEE-" + tag + "-R" + tag + "-S" + tag).toUpperCase(Locale.ROOT))));
        create("/product-variants", map("productId", product, "colorId", red, "sizeId", s, "price", 150000, "status", "ACTIVE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VARIANT_EXISTS"));
        create("/product-variants", map("productId", product, "colorId", blue, "sizeId", s, "price", 150000, "compareAtPrice", 100000, "status", "ACTIVE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_COMPARE_PRICE"));

        perform(post(API + "/product-variants/bulk"), map("productId", product, "colorIds", List.of(red, blue), "sizeIds", List.of(s, m),
                "price", 180000, "compareAtPrice", 220000, "status", "ACTIVE"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data", hasSize(3)));
        perform(get(API + "/products/" + product), null).andExpect(jsonPath("$.data.variantCount").value(4))
                .andExpect(jsonPath("$.data.minPrice").value(150000)).andExpect(jsonPath("$.data.maxPrice").value(180000));
        perform(get(API + "/product-variants").param("productId", product.toString()).param("search", "R" + tag), null)
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // Deleting and re-creating the same combination restores the original row.
        perform(delete(API + "/product-variants/" + first), null).andExpect(status().isOk());
        create("/product-variants", map("productId", product, "colorId", red, "sizeId", s, "sku", "again-" + tag, "price", 160000, "status", "INACTIVE"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(first.toString()))
                .andExpect(jsonPath("$.data.sku").value(("AGAIN-" + tag).toUpperCase(Locale.ROOT)));
        perform(put(API + "/product-variants/" + first), map("sku", "again-" + tag, "price", 170000, "status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.price").value(170000));
    }

    @Test
    void collectionsKeepProductOrderAndValidatePeriods() throws Exception {
        token = adminToken();
        UUID category = id(create("/categories", Map.of("name", "Cat " + tag, "status", "ACTIVE")));
        UUID brand = id(create("/brands", Map.of("name", "Brand " + tag, "status", "ACTIVE")));
        UUID one = id(create("/products", product("ONE-" + tag, brand, category, List.of(Map.of("url", "https://img.example.com/one.jpg")))));
        UUID two = id(create("/products", product("TWO-" + tag, brand, category, List.of())));

        Map<String, Object> request = map("name", "Hè " + tag, "status", "ACTIVE", "productIds", List.of(one, two),
                "startAt", "2026-06-01T00:00:00+07:00", "endAt", "2026-08-31T23:59:59+07:00");
        UUID collection = id(create("/collections", request).andExpect(jsonPath("$.data.productCount").value(2)));
        perform(get(API + "/collections/" + collection), null)
                .andExpect(jsonPath("$.data.products[0].id").value(one.toString()))
                .andExpect(jsonPath("$.data.products[0].imageUrl").value("https://img.example.com/one.jpg"))
                .andExpect(jsonPath("$.data.startAt").value("2026-05-31T17:00:00Z"));
        request.put("productIds", List.of(two, one));
        perform(put(API + "/collections/" + collection), request).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products[0].id").value(two.toString()))
                .andExpect(jsonPath("$.data.products[1].id").value(one.toString()));
        request.put("productIds", List.of(one, one));
        perform(put(API + "/collections/" + collection), request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCTS"));
        request.put("productIds", List.of(one));
        request.put("endAt", "2026-05-01T00:00:00+07:00");
        perform(put(API + "/collections/" + collection), request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD"));

        // A deleted product disappears from the collection without editing it.
        perform(delete(API + "/products/" + two), null).andExpect(status().isOk());
        perform(get(API + "/collections").param("search", tag), null).andExpect(jsonPath("$.data.content[0].productCount").value(1));
        perform(get(API + "/catalog/options"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brands[*].name", hasItem("Brand " + tag)));
    }

    @Test
    void catalogRoutesRequireProductPermissions() throws Exception {
        AuthResponse customer = auth.register(new RegisterRequest(UUID.randomUUID() + "@example.com", "Password-1234", "Password-1234", "Catalog Customer", null, null));
        try {
            for (String path : List.of("/categories", "/brands", "/colors", "/sizes", "/collections", "/products", "/product-variants", "/catalog/options")) {
                mvc.perform(get(API + path)).andExpect(status().isUnauthorized());
                mvc.perform(get(API + path).header(HttpHeaders.AUTHORIZATION, "Bearer " + customer.accessToken())).andExpect(status().isForbidden());
            }
            token = adminToken();
            jdbc.update("update permissions set deleted = true where code = 'PRODUCT_WRITE'");
            perform(get(API + "/brands"), null).andExpect(status().isOk());
            create("/brands", Map.of("name", "Denied " + tag, "status", "ACTIVE")).andExpect(status().isForbidden());
        } finally {
            jdbc.update("update permissions set deleted = false where code = 'PRODUCT_WRITE'");
            jdbc.update("delete from customers where account_id = ?", customer.account().id());
            jdbc.update("delete from accounts where id = ?", customer.account().id());
        }
    }

    @Test
    void taxonomyUpdatesFiltersAndSoftDeletionKeepPickerOptionsConsistent() throws Exception {
        token = adminToken();
        UUID category = id(create("/categories", map("name", "Cat " + tag, "status", "ACTIVE")));
        UUID brand = id(create("/brands", map("name", "Brand " + tag, "status", "ACTIVE")));
        UUID color = id(create("/colors", map("name", "Color " + tag, "code", "C-" + tag, "status", "ACTIVE")));
        UUID size = id(create("/sizes", map("name", "Size " + tag, "code", "S-" + tag, "sortOrder", 1, "status", "ACTIVE")));

        Map<String, UUID> ids = Map.of("categories", category, "brands", brand, "colors", color, "sizes", size);
        Map<String, Map<String, Object>> updates = Map.of(
                "categories", map("name", "Cat %_ " + tag, "status", "INACTIVE"),
                "brands", map("name", "Brand %_ " + tag, "logoUrl", "https://img.example.com/logo.png", "status", "INACTIVE"),
                "colors", map("name", "Color %_ " + tag, "code", "NEW-C-" + tag, "hexCode", "#abcdef", "status", "INACTIVE"),
                "sizes", map("name", "Size %_ " + tag, "code", "NEW-S-" + tag, "sortOrder", 5, "status", "INACTIVE"));
        for (String resource : ids.keySet()) {
            String path = "/" + resource + "/" + ids.get(resource);
            perform(put(API + path), updates.get(resource)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("INACTIVE"));
            perform(get(API + path), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value(updates.get(resource).get("name")));
            perform(get(API + "/" + resource).param("search", "%_ " + tag).param("status", "INACTIVE"), null)
                    .andExpect(jsonPath("$.data.totalElements").value(1));
            perform(get(API + "/" + resource).param("search", tag).param("status", "ACTIVE"), null)
                    .andExpect(jsonPath("$.data.totalElements").value(0));
            perform(get(API + "/catalog/options"), null)
                    .andExpect(jsonPath("$.data." + resource + "[*].id", hasItem(ids.get(resource).toString())));
            perform(delete(API + path), null).andExpect(status().isOk());
            perform(get(API + "/catalog/options"), null)
                    .andExpect(jsonPath("$.data." + resource + "[*].id", not(hasItem(ids.get(resource).toString()))));
            perform(get(API + path), null).andExpect(status().isNotFound());
        }
        // Deleting does not free identifiers for a different record.
        create("/colors", map("name", "Other " + tag, "code", "NEW-C-" + tag, "status", "ACTIVE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CODE_EXISTS"));
    }

    @Test
    void listParametersAndRequestBodiesRejectInvalidValues() throws Exception {
        token = adminToken();
        for (String resource : List.of("categories", "brands", "colors", "sizes", "collections", "products", "product-variants")) {
            for (Map.Entry<String, String> parameter : Map.of("page", "-1", "size", "51", "status", "UNKNOWN", "search", "x".repeat(255)).entrySet()) {
                perform(get(API + "/" + resource).param(parameter.getKey(), parameter.getValue()), null).andExpect(status().isBadRequest());
            }
            perform(get(API + "/" + resource + "/invalid-uuid"), null).andExpect(status().isBadRequest());
            perform(get(API + "/" + resource + "/" + UUID.randomUUID()), null).andExpect(status().isNotFound());
            create("/" + resource, Map.of()).andExpect(status().isBadRequest());
        }
        create("/colors", map("name", "Color " + tag, "code", "C-" + tag, "hexCode", "#xyz", "status", "ACTIVE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create("/brands", map("name", "Brand " + tag, "logoUrl", "javascript:alert(1)", "status", "ACTIVE"))
                .andExpect(status().isBadRequest());
        create("/sizes", map("name", "Size " + tag, "code", "S-" + tag, "sortOrder", -1, "status", "ACTIVE"))
                .andExpect(status().isBadRequest());
        create("/categories", map("name", "Cat " + tag, "status", "ACTIVE", "unknownField", true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        UUID category = id(create("/categories", map("name", "Cat " + tag, "status", "ACTIVE")));
        UUID brand = id(create("/brands", map("name", "Brand " + tag, "status", "ACTIVE")));
        Map<String, Object> request = product("P-" + tag, brand, category, List.of());
        for (Object invalidPrice : List.of(-1, 1.001, "10000000000000000")) {
            request.put("basePrice", invalidPrice);
            create("/products", request).andExpect(status().isBadRequest());
        }
        request.put("basePrice", 100);
        request.put("images", Collections.singletonList(null));
        create("/products", request).andExpect(status().isBadRequest());
        request.put("images", Collections.nCopies(11, Map.of("url", "https://img.example.com/a.png")));
        create("/products", request).andExpect(status().isBadRequest());
        request.put("images", List.of());
        request.put("brandId", UUID.randomUUID());
        create("/products", request).andExpect(status().isNotFound());
    }

    @Test
    void generatedSkusRemainDistinctForLongCodesAndBulkCreationIsRepeatable() throws Exception {
        token = adminToken();
        UUID category = id(create("/categories", map("name", "Cat " + tag, "status", "ACTIVE")));
        UUID brand = id(create("/brands", map("name", "Brand " + tag, "status", "ACTIVE")));
        UUID color = id(create("/colors", map("name", "Color " + tag, "code", "C".repeat(42) + tag, "status", "ACTIVE")));
        UUID small = id(create("/sizes", map("name", "Small " + tag, "code", "S-" + tag, "sortOrder", 1, "status", "ACTIVE")));
        UUID large = id(create("/sizes", map("name", "Large " + tag, "code", "L-" + tag, "sortOrder", 2, "status", "ACTIVE")));
        UUID product = id(create("/products", product("P".repeat(72) + tag, brand, category, List.of())));
        Map<String, Object> request = map("productId", product, "colorIds", List.of(color), "sizeIds", List.of(small, large),
                "price", 100, "status", "ACTIVE");
        JsonNode created = json.readTree(perform(post(API + "/product-variants/bulk"), request)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data", hasSize(2)))
                .andReturn().getResponse().getContentAsString()).get("data");
        String firstSku = created.get(0).get("sku").asString();
        String secondSku = created.get(1).get("sku").asString();
        assertThat(firstSku).hasSize(100).isNotEqualTo(secondSku);
        assertThat(secondSku).hasSize(100);
        perform(post(API + "/product-variants/bulk"), request).andExpect(status().isCreated()).andExpect(jsonPath("$.data", hasSize(0)));
        String firstId = created.get(0).get("id").asString();
        perform(delete(API + "/product-variants/" + firstId), null).andExpect(status().isOk());
        perform(post(API + "/product-variants/bulk"), request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(firstId))
                .andExpect(jsonPath("$.data[0].sku").value(firstSku));
    }

    @Test
    void aBulkSkuConflictRollsBackAllNewVariants() throws Exception {
        token = adminToken();
        UUID category = id(create("/categories", map("name", "Cat " + tag, "status", "ACTIVE")));
        UUID brand = id(create("/brands", map("name", "Brand " + tag, "status", "ACTIVE")));
        UUID color = id(create("/colors", map("name", "Color " + tag, "code", "C-" + tag, "status", "ACTIVE")));
        UUID small = id(create("/sizes", map("name", "Small " + tag, "code", "S-" + tag, "sortOrder", 1, "status", "ACTIVE")));
        UUID large = id(create("/sizes", map("name", "Large " + tag, "code", "L-" + tag, "sortOrder", 2, "status", "ACTIVE")));
        UUID target = id(create("/products", product("TARGET-" + tag, brand, category, List.of())));
        UUID other = id(create("/products", product("OTHER-" + tag, brand, category, List.of())));
        String reservedSku = "TARGET-" + tag + "-C-" + tag + "-L-" + tag;
        id(create("/product-variants", map("productId", other, "colorId", color, "sizeId", small,
                "sku", reservedSku, "price", 100, "status", "ACTIVE")));
        Map<String, Object> request = map("productId", target, "colorIds", List.of(color), "sizeIds", List.of(small, large),
                "price", 100, "status", "ACTIVE");
        perform(post(API + "/product-variants/bulk"), request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_EXISTS"));
        perform(get(API + "/products/" + target), null).andExpect(jsonPath("$.data.variantCount").value(0));
        assertThat(jdbc.queryForObject("select count(*) from product_variants where product_id = ?", Integer.class, target)).isZero();
    }

    @Test
    void changingOnlyImagesOrCollectionMembershipUpdatesOwnerTimestamps() throws Exception {
        token = adminToken();
        UUID category = id(create("/categories", map("name", "Cat " + tag, "status", "ACTIVE")));
        UUID brand = id(create("/brands", map("name", "Brand " + tag, "status", "ACTIVE")));
        Map<String, Object> request = product("P-" + tag, brand, category, List.of());
        UUID product = id(create("/products", request));
        var beforeImages = jdbc.queryForObject("select updated_at from products where id = ?", java.sql.Timestamp.class, product);
        request.put("images", List.of(Map.of("url", "https://img.example.com/new.png")));
        perform(put(API + "/products/" + product), request).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select updated_at from products where id = ?", java.sql.Timestamp.class, product)).isAfter(beforeImages);
        request.put("images", List.of());
        perform(put(API + "/products/" + product), request).andExpect(status().isOk()).andExpect(jsonPath("$.data.images", hasSize(0)));

        Map<String, Object> collectionRequest = map("name", "Collection " + tag, "status", "ACTIVE", "productIds", List.of());
        UUID collection = id(create("/collections", collectionRequest));
        var beforeMembership = jdbc.queryForObject("select updated_at from collections where id = ?", java.sql.Timestamp.class, collection);
        collectionRequest.put("productIds", List.of(product));
        perform(put(API + "/collections/" + collection), collectionRequest).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCount").value(1));
        assertThat(jdbc.queryForObject("select updated_at from collections where id = ?", java.sql.Timestamp.class, collection)).isAfter(beforeMembership);
        perform(delete(API + "/collections/" + collection), null).andExpect(status().isOk());
        perform(get(API + "/collections/" + collection), null).andExpect(status().isNotFound());
        perform(get(API + "/products/" + product), null).andExpect(status().isOk());
    }

    @Test
    void concurrentVariantCreationWaitsForProductDeletionThenRejectsDeletedParent() throws Exception {
        token = adminToken();
        UUID category = id(create("/categories", map("name", "Cat " + tag, "status", "ACTIVE")));
        UUID brand = id(create("/brands", map("name", "Brand " + tag, "status", "ACTIVE")));
        UUID color = id(create("/colors", map("name", "Color " + tag, "code", "C-" + tag, "status", "ACTIVE")));
        UUID size = id(create("/sizes", map("name", "Size " + tag, "code", "S-" + tag, "sortOrder", 1, "status", "ACTIVE")));
        UUID product = id(create("/products", product("P-" + tag, brand, category, List.of())));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> result = new TransactionTemplate(transactions).execute(transaction -> {
                writes.lock();
                CountDownLatch started = new CountDownLatch(1);
                Future<Integer> pending = executor.submit(() -> {
                    started.countDown();
                    return create("/product-variants", map("productId", product, "colorId", color, "sizeId", size,
                            "price", 100, "status", "ACTIVE")).andReturn().getResponse().getStatus();
                });
                try {
                    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> pending.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    perform(delete(API + "/products/" + product), null).andExpect(status().isOk());
                    return pending;
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            });
            assertThat(result.get(20, TimeUnit.SECONDS)).isEqualTo(404);
            assertThat(jdbc.queryForObject("select count(*) from product_variants where product_id = ? and not deleted", Integer.class, product)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private Map<String, Object> product(String code, UUID brand, UUID category, List<Map<String, Object>> images) {
        return map("productCode", code, "name", (code.startsWith("tee") ? "Áo Thun " : "Product ") + tag + (code.startsWith("tee") ? "" : " " + code),
                "brandId", brand, "categoryId", category, "gender", "UNISEX", "basePrice", 199000, "status", "ACTIVE", "images", images);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    private ResultActions create(String path, Map<String, ?> body) throws Exception {
        return perform(post(API + path), body);
    }

    private UUID id(ResultActions result) throws Exception {
        JsonNode node = json.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return UUID.fromString(node.get("data").get("id").asString());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }

    private String adminToken() {
        return auth.login(new LoginRequest("admin", "admin123")).accessToken();
    }
}
