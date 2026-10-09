package lemonadex.project.clothes.features.storefront.repository;

import lemonadex.project.clothes.features.storefront.model.StorefrontRows.AlertRow;
import lemonadex.project.clothes.features.storefront.model.StorefrontRows.AlertSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Back-in-stock requests. Stock counts the same way as the storefront: active warehouses, on hand minus reserved. */
@Repository @RequiredArgsConstructor
public class StockAlertRepository {
    // Plain strings with surrounding spaces: a text block before it would drop the space after "AND".
    private static final String AVAILABLE = " COALESCE((SELECT SUM(i.quantity_on_hand - i.quantity_reserved) FROM inventories i"
            + " JOIN warehouses w ON w.id = i.warehouse_id"
            + " WHERE i.product_variant_id = v.id AND NOT w.deleted AND w.status = 'ACTIVE'), 0) ";

    private final NamedParameterJdbcTemplate jdbc;

    /** Stock of a variant that is on sale; empty when the variant is not sold. */
    public OptionalLong availableIfSold(UUID variantId) {
        List<Long> found = jdbc.query("SELECT " + AVAILABLE + """
                 AS available FROM product_variants v JOIN products p ON p.id = v.product_id
                WHERE v.id = :id AND NOT v.deleted AND v.status = 'ACTIVE' AND NOT p.deleted AND p.status = 'ACTIVE'
                """, Map.of("id", variantId), (rs, i) -> rs.getLong("available"));
        return found.isEmpty() ? OptionalLong.empty() : OptionalLong.of(found.getFirst());
    }

    public int openFor(String email) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM stock_alerts WHERE lower(email) = lower(:email) AND notified_at IS NULL",
                Map.of("email", email), Integer.class);
        return count == null ? 0 : count;
    }

    /** Records the request; asking twice for the same variant and email keeps the first one. */
    public void subscribe(UUID variantId, String email, UUID customerId) {
        jdbc.update("""
                INSERT INTO stock_alerts(product_variant_id, email, customer_id) VALUES (:variant, :email, :customer)
                ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource("variant", variantId).addValue("email", email).addValue("customer", customerId));
    }

    /** Open requests whose variant is on sale and has stock again, oldest first, locked so two runs do not both mail. */
    public List<AlertRow> due(int limit) {
        return jdbc.query("""
                SELECT a.id, a.email, v.id AS variant_id, p.name, p.slug, c.name AS color_name, s.name AS size_name
                FROM stock_alerts a
                JOIN product_variants v ON v.id = a.product_variant_id
                JOIN products p ON p.id = v.product_id
                JOIN colors c ON c.id = v.color_id JOIN sizes s ON s.id = v.size_id
                WHERE a.notified_at IS NULL AND NOT v.deleted AND v.status = 'ACTIVE' AND NOT p.deleted AND p.status = 'ACTIVE'
                  AND """ + AVAILABLE + """
                 > 0
                ORDER BY a.created_at, a.id
                LIMIT :limit
                FOR UPDATE OF a SKIP LOCKED
                """, Map.of("limit", limit),
                (rs, i) -> new AlertRow(rs.getObject("id", UUID.class), rs.getString("email"), rs.getObject("variant_id", UUID.class),
                        rs.getString("name"), rs.getString("slug"), rs.getString("color_name"), rs.getString("size_name")));
    }

    public void markNotified(Collection<UUID> ids) {
        if (!ids.isEmpty()) jdbc.update("UPDATE stock_alerts SET notified_at = now() WHERE id IN (:ids)", Map.of("ids", ids));
    }

    /** Open requests grouped by variant, most wanted first. */
    public List<AlertSummaryRow> summary(int limit) {
        return jdbc.query("SELECT v.id AS variant_id, p.id AS product_id, p.name, v.sku, c.name AS color_name, s.name AS size_name, "
                        + "count(*) AS waiting, max(a.created_at) AS latest_at, " + AVAILABLE + """
                 AS available
                FROM stock_alerts a
                JOIN product_variants v ON v.id = a.product_variant_id
                JOIN products p ON p.id = v.product_id
                JOIN colors c ON c.id = v.color_id JOIN sizes s ON s.id = v.size_id
                WHERE a.notified_at IS NULL
                GROUP BY v.id, p.id, p.name, v.sku, c.name, s.name
                ORDER BY waiting DESC, latest_at DESC
                LIMIT :limit
                """, Map.of("limit", limit),
                (rs, i) -> new AlertSummaryRow(rs.getObject("variant_id", UUID.class), rs.getObject("product_id", UUID.class),
                        rs.getString("name"), rs.getString("sku"), rs.getString("color_name"), rs.getString("size_name"),
                        rs.getLong("waiting"), rs.getLong("available"), rs.getTimestamp("latest_at").toInstant()));
    }
}
