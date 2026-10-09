package lemonadex.project.clothes.features.storefront.repository;

import lemonadex.project.clothes.features.storefront.model.StorefrontRows.CartRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The signed-in customer's saved cart: variant and quantity only; names, images and prices are read live. */
@Repository @RequiredArgsConstructor
public class CartRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public List<CartRow> lines(UUID customerId) {
        return jdbc.query("""
                SELECT ci.product_variant_id, p.id AS product_id, p.slug, p.name, c.name AS color_name, s.name AS size_name, v.sku, v.price,
                       ci.quantity,
                       (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = p.id AND pi.variant_id IS NULL
                        ORDER BY pi.is_primary DESC, pi.sort_order, pi.id LIMIT 1) AS image_url,
                       COALESCE((SELECT SUM(i.quantity_on_hand - i.quantity_reserved) FROM inventories i JOIN warehouses w ON w.id = i.warehouse_id
                                 WHERE i.product_variant_id = v.id AND NOT w.deleted AND w.status = 'ACTIVE'), 0) AS available,
                       (NOT v.deleted AND v.status = 'ACTIVE' AND NOT p.deleted AND p.status = 'ACTIVE'
                        AND NOT c.deleted AND c.status = 'ACTIVE' AND NOT s.deleted AND s.status = 'ACTIVE') AS sellable
                FROM cart_items ci
                JOIN product_variants v ON v.id = ci.product_variant_id
                JOIN products p ON p.id = v.product_id
                JOIN colors c ON c.id = v.color_id JOIN sizes s ON s.id = v.size_id
                WHERE ci.customer_id = :customer
                ORDER BY ci.created_at, ci.product_variant_id
                """, Map.of("customer", customerId),
                (rs, i) -> new CartRow(rs.getObject("product_variant_id", UUID.class), rs.getObject("product_id", UUID.class),
                        rs.getString("slug"), rs.getString("name"), rs.getString("image_url"), rs.getString("color_name"),
                        rs.getString("size_name"), rs.getString("sku"), rs.getBigDecimal("price"), rs.getInt("quantity"),
                        rs.getLong("available"), rs.getBoolean("sellable")));
    }

    public void clear(UUID customerId) {
        jdbc.update("DELETE FROM cart_items WHERE customer_id = :customer", Map.of("customer", customerId));
    }

    /** Saves one line; unknown or deleted variants are skipped. */
    public void put(UUID customerId, UUID variantId, int quantity) {
        jdbc.update("""
                INSERT INTO cart_items(customer_id, product_variant_id, quantity)
                SELECT :customer, v.id, :quantity FROM product_variants v WHERE v.id = :variant AND NOT v.deleted
                ON CONFLICT (customer_id, product_variant_id) DO UPDATE SET quantity = EXCLUDED.quantity
                """, new MapSqlParameterSource("customer", customerId).addValue("variant", variantId).addValue("quantity", quantity));
    }
}
