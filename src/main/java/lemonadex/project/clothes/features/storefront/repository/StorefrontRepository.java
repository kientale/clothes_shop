package lemonadex.project.clothes.features.storefront.repository;

import lemonadex.project.clothes.features.storefront.model.StorefrontRows.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Read-only SQL for the public storefront. A product is on sale when it and at least one variant are ACTIVE and
 * not deleted, with an ACTIVE color and size; stock counts only ACTIVE, non-deleted warehouses
 * (on hand minus reserved), the same rules the order service applies when it reserves stock.
 */
@Repository @RequiredArgsConstructor
public class StorefrontRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /** Sellable variants with their available stock across active warehouses. */
    private static final String SELLABLE = """
            SELECT v.id, v.product_id, v.color_id, v.size_id, v.price, v.compare_at_price,
                   COALESCE((SELECT SUM(i.quantity_on_hand - i.quantity_reserved) FROM inventories i
                             JOIN warehouses w ON w.id = i.warehouse_id
                             WHERE i.product_variant_id = v.id AND NOT w.deleted AND w.status = 'ACTIVE'), 0) AS available
            FROM product_variants v
            JOIN colors c ON c.id = v.color_id
            JOIN sizes s ON s.id = v.size_id
            WHERE NOT v.deleted AND v.status = 'ACTIVE' AND NOT c.deleted AND c.status = 'ACTIVE'
              AND NOT s.deleted AND s.status = 'ACTIVE'
            """;

    /** Product-level image by position: 0 is the primary photo, 1 the photo shown on hover. */
    private static final String IMAGE = """
            (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = p.id AND pi.variant_id IS NULL
             ORDER BY pi.is_primary DESC, pi.sort_order, pi.id LIMIT 1 OFFSET %d)""";

    public List<CategoryRow> categories() {
        return jdbc.query("SELECT id, parent_id, name, slug FROM categories WHERE NOT deleted AND status = 'ACTIVE' ORDER BY name", Map.of(),
                (rs, i) -> new CategoryRow(uuid(rs, "id"), uuid(rs, "parent_id"), rs.getString("name"), rs.getString("slug")));
    }

    public List<BrandRow> brands() {
        return jdbc.query("SELECT id, name, slug, logo_url FROM brands WHERE NOT deleted AND status = 'ACTIVE' ORDER BY name", Map.of(),
                (rs, i) -> new BrandRow(uuid(rs, "id"), rs.getString("name"), rs.getString("slug"), rs.getString("logo_url")));
    }

    public List<ColorRow> colors() {
        return jdbc.query("SELECT id, name, code, hex_code FROM colors WHERE NOT deleted AND status = 'ACTIVE' ORDER BY name", Map.of(),
                (rs, i) -> new ColorRow(uuid(rs, "id"), rs.getString("name"), rs.getString("code"), rs.getString("hex_code")));
    }

    public List<SizeRow> sizes() {
        return jdbc.query("SELECT id, name, code, sort_order FROM sizes WHERE NOT deleted AND status = 'ACTIVE' ORDER BY sort_order, name", Map.of(),
                (rs, i) -> new SizeRow(uuid(rs, "id"), rs.getString("name"), rs.getString("code"), rs.getInt("sort_order")));
    }

    public CardPage products(ProductQuery q) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> variantFilters = new ArrayList<>();
        if (q.colorId() != null) { variantFilters.add("sv.color_id = :colorId"); params.addValue("colorId", q.colorId()); }
        if (q.sizeId() != null) { variantFilters.add("sv.size_id = :sizeId"); params.addValue("sizeId", q.sizeId()); }
        if (q.inStock()) variantFilters.add("sv.available > 0");

        List<String> filters = new ArrayList<>(List.of("NOT p.deleted", "p.status = 'ACTIVE'", "NOT b.deleted", "NOT cat.deleted"));
        if (q.search() != null && !q.search().isBlank()) {
            filters.add("(lower(p.name) LIKE :search ESCAPE '\\' OR lower(b.name) LIKE :search ESCAPE '\\')");
            String term = q.search().strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            params.addValue("search", "%" + term + "%");
        }
        if (q.categoryId() != null) {
            // The chosen category and every descendant.
            filters.add("""
                    p.category_id IN (WITH RECURSIVE tree AS (SELECT id FROM categories WHERE id = :categoryId
                        UNION ALL SELECT c.id FROM categories c JOIN tree t ON c.parent_id = t.id) SELECT id FROM tree)""");
            params.addValue("categoryId", q.categoryId());
        }
        if (q.brandId() != null) { filters.add("p.brand_id = :brandId"); params.addValue("brandId", q.brandId()); }
        if (q.gender() != null) { filters.add("p.gender = :gender"); params.addValue("gender", q.gender()); }
        if (q.minPrice() != null) { filters.add("agg.min_price >= :minPrice"); params.addValue("minPrice", q.minPrice()); }
        if (q.maxPrice() != null) { filters.add("agg.min_price <= :maxPrice"); params.addValue("maxPrice", q.maxPrice()); }
        if (q.collectionId() != null) {
            filters.add("p.id IN (SELECT cp.product_id FROM collection_products cp WHERE cp.collection_id = :collectionId)");
            params.addValue("collectionId", q.collectionId());
        }
        if (q.wishlistOf() != null) {
            filters.add("p.id IN (SELECT wi.product_id FROM wishlist_items wi WHERE wi.customer_id = :wishlistOf)");
            params.addValue("wishlistOf", q.wishlistOf());
        }

        String base = "WITH sv AS (" + SELLABLE + "),\n"
                + "agg AS (SELECT sv.product_id, MIN(sv.price) AS min_price, MAX(sv.price) AS max_price,\n"
                + "        MAX(sv.compare_at_price) AS compare_at, SUM(sv.available) AS available\n"
                + "        FROM sv " + (variantFilters.isEmpty() ? "" : "WHERE " + String.join(" AND ", variantFilters)) + " GROUP BY sv.product_id)\n"
                + "SELECT {columns} FROM products p\n"
                + "JOIN agg ON agg.product_id = p.id\n"
                + "JOIN brands b ON b.id = p.brand_id\n"
                + "JOIN categories cat ON cat.id = p.category_id\n"
                + "WHERE " + String.join(" AND ", filters);

        Long total = jdbc.queryForObject(base.replace("{columns}", "count(*)"), params, Long.class);
        String order = switch (q.sort() == null ? "" : q.sort()) {
            case "price_asc" -> "agg.min_price ASC, p.created_at DESC";
            case "price_desc" -> "agg.min_price DESC, p.created_at DESC";
            case "name" -> "p.name ASC";
            case "collection" -> "(SELECT cp.sort_order FROM collection_products cp WHERE cp.collection_id = :collectionId AND cp.product_id = p.id) ASC";
            case "wishlist" -> "(SELECT wi.created_at FROM wishlist_items wi WHERE wi.customer_id = :wishlistOf AND wi.product_id = p.id) DESC";
            default -> "p.created_at DESC";
        };
        params.addValue("limit", q.size()).addValue("offset", (long) q.page() * q.size());
        String columns = "p.id, p.slug, p.name, b.name AS brand_name, cat.name AS category_name, p.gender, p.created_at,\n"
                + "agg.min_price, agg.max_price, agg.compare_at, agg.available, " + IMAGE.formatted(0) + " AS image_url, "
                + IMAGE.formatted(1) + " AS hover_image_url";
        List<CardRow> rows = jdbc.query(base.replace("{columns}", columns) + "\nORDER BY " + order + ", p.id LIMIT :limit OFFSET :offset", params,
                (rs, i) -> new CardRow(uuid(rs, "id"), rs.getString("slug"), rs.getString("name"), rs.getString("brand_name"),
                        rs.getString("category_name"), rs.getString("gender"), rs.getTimestamp("created_at").toInstant(), rs.getBigDecimal("min_price"),
                        rs.getBigDecimal("max_price"), rs.getBigDecimal("compare_at"), rs.getLong("available"), rs.getString("image_url"),
                        rs.getString("hover_image_url"), List.of()));
        Map<UUID, List<SwatchRow>> swatches = swatches(rows.stream().map(CardRow::id).toList());
        List<CardRow> withSwatches = rows.stream().map(r -> new CardRow(r.id(), r.slug(), r.name(), r.brandName(), r.categoryName(), r.gender(),
                r.createdAt(), r.minPrice(), r.maxPrice(), r.compareAtPrice(), r.available(), r.imageUrl(), r.hoverImageUrl(),
                swatches.getOrDefault(r.id(), List.of()))).toList();
        return new CardPage(withSwatches, total == null ? 0 : total);
    }

    private Map<UUID, List<SwatchRow>> swatches(List<UUID> productIds) {
        if (productIds.isEmpty()) return Map.of();
        Map<UUID, List<SwatchRow>> result = new HashMap<>();
        jdbc.query("SELECT DISTINCT sv.product_id, c.name, c.hex_code FROM (" + SELLABLE + ") sv JOIN colors c ON c.id = sv.color_id\n"
                        + "WHERE sv.product_id IN (:ids) ORDER BY c.name", Map.of("ids", productIds),
                (ResultSet rs) -> {
                    result.computeIfAbsent(uuid(rs, "product_id"), k -> new ArrayList<>()).add(new SwatchRow(rs.getString("name"), rs.getString("hex_code")));
                });
        return result;
    }

    /** An on-sale product by id or slug. */
    public Optional<ProductHead> product(UUID id, String slug) {
        return jdbc.query("""
                SELECT p.id, p.slug, p.product_code, p.name, p.short_description, p.description, p.material, p.gender,
                       b.id AS brand_id, b.name AS brand_name, b.slug AS brand_slug,
                       cat.id AS category_id, cat.name AS category_name, cat.slug AS category_slug
                FROM products p JOIN brands b ON b.id = p.brand_id JOIN categories cat ON cat.id = p.category_id
                WHERE NOT p.deleted AND p.status = 'ACTIVE' AND NOT b.deleted AND NOT cat.deleted
                  AND (p.id = :id OR p.slug = :slug)
                """, new MapSqlParameterSource("id", id).addValue("slug", slug),
                (rs, i) -> new ProductHead(uuid(rs, "id"), rs.getString("slug"), rs.getString("product_code"), rs.getString("name"),
                        rs.getString("short_description"), rs.getString("description"), rs.getString("material"), rs.getString("gender"),
                        new NamedRow(uuid(rs, "brand_id"), rs.getString("brand_name"), rs.getString("brand_slug")),
                        new NamedRow(uuid(rs, "category_id"), rs.getString("category_name"), rs.getString("category_slug")))).stream().findFirst();
    }

    public List<ImageRow> images(UUID productId) {
        return jdbc.query("""
                SELECT image_url, alt_text FROM product_images WHERE product_id = :id AND variant_id IS NULL
                ORDER BY is_primary DESC, sort_order, id
                """, Map.of("id", productId), (rs, i) -> new ImageRow(rs.getString("image_url"), rs.getString("alt_text")));
    }

    public List<VariantRow> variants(UUID productId) {
        return jdbc.query("SELECT sv.id, v.sku, c.id AS color_id, c.name AS color_name, c.hex_code, s.id AS size_id, s.name AS size_name,\n"
                        + "       s.sort_order, sv.price, sv.compare_at_price, sv.available\n"
                        + "FROM (" + SELLABLE + ") sv JOIN product_variants v ON v.id = sv.id JOIN colors c ON c.id = sv.color_id\n"
                        + "JOIN sizes s ON s.id = sv.size_id WHERE sv.product_id = :id ORDER BY c.name, s.sort_order, s.name",
                Map.of("id", productId),
                (rs, i) -> new VariantRow(uuid(rs, "id"), rs.getString("sku"), uuid(rs, "color_id"), rs.getString("color_name"),
                        rs.getString("hex_code"), uuid(rs, "size_id"), rs.getString("size_name"), rs.getInt("sort_order"),
                        rs.getBigDecimal("price"), rs.getBigDecimal("compare_at_price"), rs.getLong("available")));
    }

    public RatingSummary rating(UUID productId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(AVG(rating), 0) AS average, COUNT(*) AS total FROM product_reviews
                WHERE product_id = :id AND status = 'APPROVED' AND NOT deleted
                """, Map.of("id", productId), (rs, i) -> new RatingSummary(rs.getDouble("average"), rs.getLong("total")));
    }

    public List<ReviewRow> reviews(UUID productId, int limit) {
        List<ReviewRow> rows = jdbc.query("""
                SELECT r.id, r.rating, r.comment, r.created_at, c.full_name FROM product_reviews r JOIN customers c ON c.id = r.customer_id
                WHERE r.product_id = :id AND r.status = 'APPROVED' AND NOT r.deleted ORDER BY r.created_at DESC, r.id LIMIT :limit
                """, new MapSqlParameterSource("id", productId).addValue("limit", limit),
                (rs, i) -> new ReviewRow(uuid(rs, "id"), rs.getInt("rating"), rs.getString("comment"), shortName(rs.getString("full_name")),
                        List.of(), rs.getTimestamp("created_at").toInstant()));
        if (rows.isEmpty()) return rows;
        Map<UUID, List<String>> images = new HashMap<>();
        jdbc.query("SELECT review_id, image_url FROM review_images WHERE review_id IN (:ids) ORDER BY sort_order",
                Map.of("ids", rows.stream().map(ReviewRow::id).toList()),
                (ResultSet rs) -> {
                    images.computeIfAbsent(uuid(rs, "review_id"), k -> new ArrayList<>()).add(rs.getString("image_url"));
                });
        return rows.stream().map(r -> new ReviewRow(r.id(), r.rating(), r.comment(), r.customerName(), images.getOrDefault(r.id(), List.of()), r.createdAt())).toList();
    }

    /** Banners that are switched on and inside their schedule now, optionally for one position. */
    public List<BannerRow> banners(String position) {
        return jdbc.query("""
                SELECT id, title, image_url, link_url, position, sort_order FROM banners
                WHERE NOT deleted AND status = 'ACTIVE' AND (start_at IS NULL OR start_at <= now()) AND (end_at IS NULL OR end_at > now())
                  AND (CAST(:position AS VARCHAR) IS NULL OR position = :position)
                ORDER BY position, sort_order, created_at DESC, id
                """, new MapSqlParameterSource("position", position),
                (rs, i) -> new BannerRow(uuid(rs, "id"), rs.getString("title"), rs.getString("image_url"), rs.getString("link_url"),
                        rs.getString("position"), rs.getInt("sort_order")));
    }

    // A plain string with a leading space: text blocks drop the trailing space of the "WHERE " it follows.
    private static final String VISIBLE_COLLECTION = " NOT col.deleted AND col.status = 'ACTIVE'"
            + " AND (col.start_at IS NULL OR col.start_at <= now()) AND (col.end_at IS NULL OR col.end_at > now())";

    /** Collections switched on and inside their schedule, with how many of their products are on sale. */
    public List<CollectionRow> collections(String slug) {
        return jdbc.query("""
                SELECT col.id, col.slug, col.name, col.description, col.image_url, col.start_at, col.end_at,
                       (SELECT count(*) FROM collection_products cp JOIN products p ON p.id = cp.product_id
                        WHERE cp.collection_id = col.id AND NOT p.deleted AND p.status = 'ACTIVE') AS product_count
                FROM collections col
                WHERE """ + VISIBLE_COLLECTION + """

                  AND (CAST(:slug AS VARCHAR) IS NULL OR col.slug = :slug)
                ORDER BY col.start_at DESC NULLS LAST, col.created_at DESC, col.id
                """, new MapSqlParameterSource("slug", slug),
                (rs, i) -> new CollectionRow(uuid(rs, "id"), rs.getString("slug"), rs.getString("name"), rs.getString("description"),
                        rs.getString("image_url"), rs.getLong("product_count"), instant(rs, "start_at"), instant(rs, "end_at")));
    }

    /** Public pages for the sitemap: on-sale products and visible collections. */
    public List<SitemapEntry> sitemap() {
        List<SitemapEntry> entries = new ArrayList<>(jdbc.query("""
                SELECT '/shop/products/' || p.slug AS path, p.updated_at FROM products p
                WHERE NOT p.deleted AND p.status = 'ACTIVE'
                  AND EXISTS (SELECT 1 FROM (""" + SELLABLE + """
                ) sv WHERE sv.product_id = p.id)
                ORDER BY p.updated_at DESC
                """, Map.of(), (rs, i) -> new SitemapEntry(rs.getString("path"), instant(rs, "updated_at"))));
        entries.addAll(jdbc.query("SELECT '/shop/collections/' || col.slug AS path, col.updated_at FROM collections col WHERE " + VISIBLE_COLLECTION,
                Map.of(), (rs, i) -> new SitemapEntry(rs.getString("path"), instant(rs, "updated_at"))));
        entries.addAll(jdbc.query("""
                SELECT '/shop/articles/' || slug AS path, updated_at FROM articles
                WHERE NOT deleted AND status = 'PUBLISHED' AND (published_at IS NULL OR published_at <= now())
                ORDER BY published_at DESC NULLS LAST
                """, Map.of(), (rs, i) -> new SitemapEntry(rs.getString("path"), instant(rs, "updated_at"))));
        return entries;
    }

    /** The product's size chart as stored JSON, if the shop has written one. */
    public Optional<String> sizeChart(UUID productId) {
        return jdbc.query("SELECT chart::text AS chart FROM product_size_charts WHERE product_id = :id", Map.of("id", productId),
                (rs, i) -> rs.getString("chart")).stream().findFirst();
    }

    /**
     * Variants of the same product at the given price that an item can be exchanged for, with the stock of the
     * order's warehouse (exchanges are reserved there).
     */
    public List<ExchangeRow> exchangeOptions(UUID originalVariantId, UUID warehouseId, java.math.BigDecimal price) {
        return jdbc.query("""
                SELECT v.id, c.name AS color_name, s.name AS size_name,
                       COALESCE((SELECT i.quantity_on_hand - i.quantity_reserved FROM inventories i
                                 WHERE i.product_variant_id = v.id AND i.warehouse_id = :warehouse), 0) AS available
                FROM product_variants ov
                JOIN product_variants v ON v.product_id = ov.product_id
                JOIN colors c ON c.id = v.color_id JOIN sizes s ON s.id = v.size_id
                WHERE ov.id = :original AND v.id <> ov.id AND NOT v.deleted AND v.status = 'ACTIVE' AND v.price = :price
                  AND NOT c.deleted AND c.status = 'ACTIVE' AND NOT s.deleted AND s.status = 'ACTIVE'
                ORDER BY c.name, s.sort_order, s.name
                """, new MapSqlParameterSource("original", originalVariantId).addValue("warehouse", warehouseId).addValue("price", price),
                (rs, i) -> new ExchangeRow(uuid(rs, "id"), rs.getString("color_name"), rs.getString("size_name"), rs.getLong("available")));
    }

    /**
     * The guest customer (no account) for a phone number, created on first use, so a guest's orders and coupon
     * usage stay together.
     */
    public UUID guestCustomer(String fullName, String phone) {
        return jdbc.query("""
                SELECT id FROM customers WHERE account_id IS NULL AND phone = :phone AND NOT deleted AND status = 'ACTIVE'
                ORDER BY created_at, id LIMIT 1
                """, Map.of("phone", phone), (rs, i) -> uuid(rs, "id")).stream().findFirst()
                .orElseGet(() -> jdbc.queryForObject("INSERT INTO customers(full_name, phone) VALUES (:name, :phone) RETURNING id",
                        new MapSqlParameterSource("name", fullName).addValue("phone", phone), UUID.class));
    }

    public void setContactEmail(UUID orderId, String email) {
        jdbc.update("UPDATE orders SET contact_email = :email WHERE id = :id", new MapSqlParameterSource("id", orderId).addValue("email", email));
    }

    /** Current price of each variant, for estimates before the order prices them for real. */
    public Map<UUID, java.math.BigDecimal> prices(Collection<UUID> variantIds) {
        Map<UUID, java.math.BigDecimal> result = new HashMap<>();
        jdbc.query("SELECT id, price FROM product_variants WHERE id IN (:ids) AND NOT deleted", Map.of("ids", variantIds),
                (ResultSet rs) -> {
                    result.put(uuid(rs, "id"), rs.getBigDecimal("price"));
                });
        return result;
    }

    /** Order id for a code and the phone printed on it; digits only, so "0901 234 567" matches "0901234567". */
    public Optional<UUID> orderByCodeAndPhone(String orderCode, String phoneDigits) {
        return jdbc.query("""
                SELECT id FROM orders WHERE upper(order_code) = upper(:code)
                  AND regexp_replace(recipient_phone, '[^0-9]', '', 'g') = :phone
                """, new MapSqlParameterSource("code", orderCode).addValue("phone", phoneDigits),
                (rs, i) -> uuid(rs, "id")).stream().findFirst();
    }

    /** Email of a customer's account, for the order confirmation. */
    public Optional<String> accountEmail(UUID accountId) {
        return jdbc.query("SELECT email FROM accounts WHERE id = :id AND NOT deleted", Map.of("id", accountId),
                (rs, i) -> rs.getString("email")).stream().findFirst();
    }

    /** Active customer profile linked to the account, if any. */
    public Optional<UUID> customerOf(UUID accountId) {
        return jdbc.query("SELECT id FROM customers WHERE account_id = :id AND NOT deleted AND status = 'ACTIVE'", Map.of("id", accountId),
                (rs, i) -> uuid(rs, "id")).stream().findFirst();
    }

    /** Available quantity per active warehouse and variant, for warehouses holding any of the variants. */
    public Map<UUID, Map<UUID, Long>> availability(Collection<UUID> variantIds) {
        Map<UUID, Map<UUID, Long>> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT i.warehouse_id, i.product_variant_id, i.quantity_on_hand - i.quantity_reserved AS available
                FROM inventories i JOIN warehouses w ON w.id = i.warehouse_id
                WHERE NOT w.deleted AND w.status = 'ACTIVE' AND i.product_variant_id IN (:ids)
                ORDER BY w.name, w.id
                """, Map.of("ids", variantIds), (ResultSet rs) -> {
            result.computeIfAbsent(uuid(rs, "warehouse_id"), k -> new HashMap<>()).put(uuid(rs, "product_variant_id"), rs.getLong("available"));
        });
        return result;
    }

    /** "Nguyễn Minh Thư" becomes "Minh Thư N." so public reviews never print a full name. */
    static String shortName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Khách hàng";
        String[] parts = fullName.strip().split("\\s+");
        if (parts.length == 1) return parts[0];
        String given = String.join(" ", Arrays.copyOfRange(parts, Math.max(1, parts.length - 2), parts.length));
        return given + " " + parts[0].charAt(0) + ".";
    }

    private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }
}
