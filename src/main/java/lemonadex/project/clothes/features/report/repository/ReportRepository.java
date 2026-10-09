package lemonadex.project.clothes.features.report.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Aggregate child tables first: joining payment/return/item rows directly would multiply monetary totals. */
@Repository @RequiredArgsConstructor
public class ReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public boolean timezoneExists(String timezone) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name = :zone)", Map.of("zone", timezone), Boolean.class));
    }
    public List<Map<String, Object>> revenue(Map<String, Object> p) {
        return jdbc.queryForList("""
            WITH events AS (
              SELECT p.paid_at occurred_at, p.amount paid, 0::numeric refunded FROM payments p JOIN orders o ON o.id = p.order_id
              WHERE p.status = 'PAID' AND p.paid_at >= :from AND p.paid_at < :to AND (CAST(:warehouse AS uuid) IS NULL OR o.warehouse_id = :warehouse)
              UNION ALL
              SELECT r.processed_at, 0::numeric, r.amount FROM refunds r JOIN payments p ON p.id = r.payment_id JOIN orders o ON o.id = p.order_id
              WHERE r.status = 'SUCCEEDED' AND r.processed_at >= :from AND r.processed_at < :to AND (CAST(:warehouse AS uuid) IS NULL OR o.warehouse_id = :warehouse)
            ) SELECT date_trunc(:bucket, occurred_at AT TIME ZONE :zone)::date bucket_start,
                sum(paid) paid_amount, sum(refunded) refunded_amount FROM events GROUP BY 1 ORDER BY 1
            """, p);
    }
    private static final String ORDERS = """
        WITH selected AS (SELECT * FROM orders WHERE placed_at >= :from AND placed_at < :to
            AND (CAST(:warehouse AS uuid) IS NULL OR warehouse_id = :warehouse))
        """;
    public List<Map<String, Object>> orderStatuses(Map<String, Object> p) {
        return jdbc.queryForList(ORDERS + "SELECT order_status status, count(*) total, coalesce(sum(total_amount),0) amount FROM selected GROUP BY order_status ORDER BY order_status", p);
    }
    public List<Map<String, Object>> orderSeries(Map<String, Object> p) {
        return jdbc.queryForList(ORDERS + """
            SELECT date_trunc(:bucket, placed_at AT TIME ZONE :zone)::date bucket_start, count(*) order_count,
                count(*) FILTER(WHERE order_status = 'CANCELLED') cancelled_count,
                coalesce(sum(total_amount) FILTER(WHERE order_status <> 'CANCELLED'),0) order_amount
            FROM selected GROUP BY 1 ORDER BY 1
            """, p);
    }
    public List<Map<String, Object>> bestsellers(Map<String, Object> p) {
        return jdbc.queryForList(ORDERS + """
            , line_returns AS (SELECT ri.order_item_id, sum(ri.quantity) quantity FROM return_request_items ri
                JOIN return_requests r ON r.id = ri.return_request_id WHERE r.status = 'COMPLETED' AND r.request_type = 'RETURN' GROUP BY ri.order_item_id),
            order_bases AS (SELECT i.order_id, sum(i.total_amount) net, sum(i.discount_amount) line_discount FROM order_items i
                JOIN selected o ON o.id = i.order_id GROUP BY i.order_id),
            rows AS (
                SELECT v.product_id, i.product_name, pr.deleted, o.id order_id, o.placed_at, i.id item_id, i.quantity,
                  coalesce(r.quantity,0) returned,
                  CASE WHEN b.net = 0 THEN 0 ELSE
                    (i.total_amount - (o.discount_amount-b.line_discount)*i.total_amount/b.net) *
                    (i.quantity-coalesce(r.quantity,0)) / i.quantity END merchandise
                FROM selected o JOIN order_items i ON i.order_id = o.id JOIN product_variants v ON v.id = i.product_variant_id
                JOIN products pr ON pr.id = v.product_id JOIN order_bases b ON b.order_id = o.id LEFT JOIN line_returns r ON r.order_item_id = i.id
                WHERE o.order_status IN ('DELIVERED','COMPLETED') AND (CAST(:product AS uuid) IS NULL OR v.product_id = :product)
            ), result AS (
                SELECT product_id, (array_agg(product_name ORDER BY placed_at DESC, item_id))[1] product_name,
                  bool_or(deleted) deleted, count(DISTINCT order_id) order_count, sum(quantity) gross_quantity,
                  sum(returned) returned_quantity, sum(quantity-returned) net_quantity, round(sum(merchandise),2) merchandise_amount
                FROM rows GROUP BY product_id
            ) SELECT *, count(*) OVER() total_rows FROM result ORDER BY net_quantity DESC, merchandise_amount DESC, product_id LIMIT :size OFFSET :offset
            """, p);
    }
    private static final String STOCKS = """
        WITH stocks AS (SELECT i.id inventory_id, w.id warehouse_id, w.name warehouse_name, w.deleted warehouse_deleted,
            v.id variant_id, pr.id product_id, pr.name product_name, v.sku, pr.deleted product_deleted, v.deleted variant_deleted,
            i.quantity_on_hand, i.quantity_reserved, i.quantity_on_hand-i.quantity_reserved quantity_available,
            v.price unit_retail_price, v.price*i.quantity_on_hand retail_value
            FROM inventories i JOIN warehouses w ON w.id = i.warehouse_id JOIN product_variants v ON v.id = i.product_variant_id
            JOIN products pr ON pr.id = v.product_id WHERE (CAST(:warehouse AS uuid) IS NULL OR w.id = :warehouse)
              AND (CAST(:product AS uuid) IS NULL OR pr.id = :product)
              AND (CAST(:search AS text) IS NULL OR lower(v.sku) LIKE :search ESCAPE '\\' OR lower(pr.name) LIKE :search ESCAPE '\\')
              AND (NOT :low_stock OR i.quantity_on_hand-i.quantity_reserved <= :threshold))
        """;
    public Map<String, Object> inventorySummary(Map<String, Object> p) {
        return jdbc.queryForMap(STOCKS + "SELECT count(*) stock_records, coalesce(sum(quantity_on_hand),0) on_hand, coalesce(sum(quantity_reserved),0) reserved, coalesce(sum(quantity_available),0) available, coalesce(sum(retail_value),0) retail_value FROM stocks", p);
    }
    public List<Map<String, Object>> inventoryRows(Map<String, Object> p) {
        return jdbc.queryForList(STOCKS + "SELECT * FROM stocks ORDER BY quantity_available, warehouse_id, variant_id LIMIT :size OFFSET :offset", p);
    }
    private static final String CUSTOMERS = ORDERS + """
        , refunds_by_order AS (SELECT p.order_id, sum(r.amount) amount FROM refunds r JOIN payments p ON p.id = r.payment_id
            WHERE r.status = 'SUCCEEDED' GROUP BY p.order_id), customers_report AS (
            SELECT c.id customer_id, c.full_name, c.status, c.deleted, count(*) delivered_orders,
                sum(o.total_amount) order_amount, sum(coalesce(r.amount,0)) refunded_amount,
                sum(o.total_amount-coalesce(r.amount,0)) net_order_amount
            FROM selected o JOIN customers c ON c.id = o.customer_id LEFT JOIN refunds_by_order r ON r.order_id = o.id
            WHERE o.order_status IN ('DELIVERED','COMPLETED') GROUP BY c.id)
        """;
    public Map<String, Object> customerSummary(Map<String, Object> p) {
        return jdbc.queryForMap(CUSTOMERS + """
            SELECT (SELECT count(*) FROM customers WHERE created_at >= :from AND created_at < :to) new_customers,
                (SELECT count(*) FROM customers WHERE NOT deleted) total_customers,
                (SELECT count(*) FROM customers WHERE NOT deleted AND status = 'ACTIVE') active_customers,
                count(*) purchasing_customers, count(*) FILTER(WHERE delivered_orders > 1) repeat_customers FROM customers_report
            """, p);
    }
    public List<Map<String, Object>> customerRows(Map<String, Object> p) {
        return jdbc.queryForList(CUSTOMERS + "SELECT * FROM customers_report ORDER BY net_order_amount DESC, delivered_orders DESC, customer_id LIMIT :size OFFSET :offset", p);
    }
    private static final String RETURNS = """
        WITH selected AS (SELECT r.id return_request_id, o.id order_id, o.order_code, r.customer_id, r.request_type,
            r.status, r.reason, r.requested_at, r.completed_at,
            coalesce((SELECT sum(i.quantity) FROM return_request_items i WHERE i.return_request_id = r.id),0) quantity,
            coalesce((SELECT sum(f.amount) FROM refunds f WHERE f.return_request_id = r.id AND f.status = 'SUCCEEDED'),0) refunded_amount
            FROM return_requests r JOIN orders o ON o.id = r.order_id WHERE r.requested_at >= :from AND r.requested_at < :to
            AND (CAST(:warehouse AS uuid) IS NULL OR o.warehouse_id = :warehouse)
            AND (CAST(:status AS text) IS NULL OR r.status = :status) AND (CAST(:type AS text) IS NULL OR r.request_type = :type))
        """;
    public Map<String, Object> returnSummary(Map<String, Object> p) {
        return jdbc.queryForMap(RETURNS + """
            SELECT count(*) total_requests, count(*) FILTER(WHERE request_type = 'RETURN') return_requests,
                count(*) FILTER(WHERE request_type = 'EXCHANGE') exchange_requests, coalesce(sum(quantity),0) requested_quantity,
                coalesce(sum(quantity) FILTER(WHERE status = 'COMPLETED'),0) completed_quantity, coalesce(sum(refunded_amount),0) refunded_amount FROM selected
            """, p);
    }
    public List<Map<String, Object>> returnStatuses(Map<String, Object> p) {
        return jdbc.queryForList(RETURNS + "SELECT status, count(*) total, sum(refunded_amount) amount FROM selected GROUP BY status ORDER BY status", p);
    }
    public List<Map<String, Object>> returnRows(Map<String, Object> p) {
        return jdbc.queryForList(RETURNS + "SELECT * FROM selected ORDER BY requested_at DESC, return_request_id LIMIT :size OFFSET :offset", p);
    }
    public List<Map<String, Object>> campaignRows(Map<String, Object> p) {
        return jdbc.queryForList(ORDERS + """
            , allocations AS (
                SELECT 'COUPON' campaign_type, c.id campaign_id, c.name, c.code, c.deleted, o.id order_id, o.total_amount,
                    u.released OR o.order_status = 'CANCELLED' released, 0::bigint quantity, u.discount_amount
                FROM coupon_usages u JOIN coupons c ON c.id = u.coupon_id JOIN selected o ON o.id = u.order_id
                UNION ALL
                SELECT 'PROMOTION', pr.id, pr.name, NULL, pr.deleted, o.id, o.total_amount,
                    m.released OR o.order_status = 'CANCELLED', m.quantity, m.discount_amount
                FROM order_marketing_lines m JOIN promotions pr ON pr.id = m.promotion_id
                    JOIN order_items i ON i.id = m.order_item_id JOIN selected o ON o.id = i.order_id
                UNION ALL
                SELECT 'FLASH_SALE', f.id, f.name, NULL, f.deleted, o.id, o.total_amount,
                    m.released OR o.order_status = 'CANCELLED', m.quantity, m.discount_amount
                FROM order_marketing_lines m JOIN flash_sale_items fi ON fi.id = m.flash_sale_item_id JOIN flash_sales f ON f.id = fi.flash_sale_id
                    JOIN order_items i ON i.id = m.order_item_id JOIN selected o ON o.id = i.order_id
            ), per_order AS (
                SELECT campaign_type, campaign_id, name, code, deleted, order_id, total_amount, bool_or(released) released,
                    sum(quantity) quantity, sum(discount_amount) discount_amount FROM allocations GROUP BY campaign_type, campaign_id, name, code, deleted, order_id, total_amount
            ), result AS (
                SELECT campaign_type, campaign_id, name, code, deleted, count(*) FILTER(WHERE NOT released) order_count,
                  count(*) FILTER(WHERE released) released_order_count, coalesce(sum(quantity) FILTER(WHERE NOT released),0) quantity,
                  coalesce(sum(discount_amount) FILTER(WHERE NOT released),0) discount_amount,
                  coalesce(sum(total_amount) FILTER(WHERE NOT released),0) attributed_order_amount FROM per_order
                WHERE (CAST(:type AS text) IS NULL OR campaign_type = :type) GROUP BY campaign_type, campaign_id, name, code, deleted
            ) SELECT *, count(*) OVER() total_rows FROM result ORDER BY order_count DESC, discount_amount DESC, campaign_type, campaign_id LIMIT :size OFFSET :offset
            """, p);
    }
}
