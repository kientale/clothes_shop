package lemonadex.project.clothes.features.marketing.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Scalar cross-feature references keep campaign management independent of order entities. */
@Repository @RequiredArgsConstructor
public class MarketingQueryRepository {
    private final EntityManager em;
    public boolean exists(String table, UUID id) {
        if (!Set.of("products", "categories", "customers").contains(table)) throw new IllegalArgumentException("Unsupported reference");
        return (Boolean) em.createNativeQuery("SELECT EXISTS(SELECT 1 FROM " + table + " WHERE id = :id AND NOT deleted)")
                .setParameter("id", id).getSingleResult();
    }
    public boolean duplicateCoupon(String code, UUID excluded) {
        return (Boolean) em.createNativeQuery("SELECT EXISTS(SELECT 1 FROM coupons WHERE lower(code) = lower(:code) AND (CAST(:id AS uuid) IS NULL OR id <> CAST(:id AS uuid)))")
                .setParameter("code", code).setParameter("id", excluded).getSingleResult();
    }
    public UUID ownCustomer(UUID accountId) {
        List<?> ids = em.createNativeQuery("SELECT id FROM customers WHERE account_id = :id AND NOT deleted AND status = 'ACTIVE'")
                .setParameter("id", accountId).getResultList();
        return ids.isEmpty() ? null : (UUID) ids.getFirst();
    }
    public UUID category(UUID productId) {
        return (UUID) em.createNativeQuery("SELECT category_id FROM products WHERE id = :id").setParameter("id", productId).getSingleResult();
    }
    public long customerUses(UUID couponId, UUID customerId) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM coupon_usages WHERE coupon_id = :coupon AND customer_id = :customer AND NOT released")
                .setParameter("coupon", couponId).setParameter("customer", customerId).getSingleResult()).longValue();
    }
    public void recordCoupon(UUID couponId, UUID customerId, UUID orderId, java.math.BigDecimal discount) {
        em.createNativeQuery("INSERT INTO coupon_usages(coupon_id, customer_id, order_id, discount_amount) VALUES(:coupon, :customer, :order, :discount)")
                .setParameter("coupon", couponId).setParameter("customer", customerId).setParameter("order", orderId).setParameter("discount", discount).executeUpdate();
    }
    public void recordLine(UUID itemId, UUID flashId, UUID promotionId, int quantity, java.math.BigDecimal discount) {
        em.createNativeQuery("INSERT INTO order_marketing_lines(order_item_id, flash_sale_item_id, promotion_id, quantity, discount_amount) VALUES(:item, :flash, :promotion, :quantity, :discount)")
                .setParameter("item", itemId).setParameter("flash", flashId).setParameter("promotion", promotionId)
                .setParameter("quantity", quantity).setParameter("discount", discount).executeUpdate();
    }
    public void release(UUID orderId) {
        // UPDATE ... RETURNING makes a repeated cancellation harmless and retains the audit rows.
        em.createNativeQuery("""
            WITH released AS (UPDATE coupon_usages SET released = true, released_at = CURRENT_TIMESTAMP
                WHERE order_id = :id AND NOT released RETURNING coupon_id)
            UPDATE coupons c SET used_count = used_count - x.total FROM
                (SELECT coupon_id, count(*)::integer total FROM released GROUP BY coupon_id) x WHERE c.id = x.coupon_id
            """).setParameter("id", orderId).executeUpdate();
        em.createNativeQuery("""
            WITH released AS (UPDATE order_marketing_lines m SET released = true FROM order_items i
                WHERE m.order_item_id = i.id AND i.order_id = :id AND NOT m.released RETURNING m.flash_sale_item_id, m.quantity)
            UPDATE flash_sale_items f SET sold_quantity = sold_quantity - x.total FROM
                (SELECT flash_sale_item_id, sum(quantity)::integer total FROM released WHERE flash_sale_item_id IS NOT NULL GROUP BY flash_sale_item_id) x
                WHERE f.id = x.flash_sale_item_id
            """).setParameter("id", orderId).executeUpdate();
    }
    public int publish(UUID notificationId, boolean all) {
        return em.createNativeQuery("""
            INSERT INTO customer_notifications(notification_id, customer_id)
            SELECT :id, c.id FROM customers c WHERE NOT c.deleted AND c.status = 'ACTIVE'
              AND (:all OR EXISTS(SELECT 1 FROM notification_targets t WHERE t.notification_id = :id AND t.customer_id = c.id))
            ON CONFLICT(notification_id, customer_id) DO NOTHING
            """).setParameter("id", notificationId).setParameter("all", all).executeUpdate();
    }
}
