package lemonadex.project.clothes.features.review.repository;
import lemonadex.project.clothes.features.review.model.ProductReview;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID>, JpaSpecificationExecutor<ProductReview> {
    @Modifying @Query(value = "UPDATE product_reviews SET deleted = true, updated_at = CURRENT_TIMESTAMP WHERE id = :id", nativeQuery = true)
    void archive(UUID id);
    @Query(value = """
        SELECT v.product_id AS productId FROM order_items i JOIN orders o ON o.id = i.order_id
        JOIN product_variants v ON v.id = i.product_variant_id JOIN customers c ON c.id = o.customer_id
        WHERE i.id = :itemId AND o.customer_id = :customerId AND NOT c.deleted AND c.status = 'ACTIVE'
          AND o.order_status IN ('DELIVERED', 'COMPLETED')
          AND i.quantity > coalesce((SELECT sum(ri.quantity) FROM return_request_items ri
              JOIN return_requests r ON r.id = ri.return_request_id WHERE ri.order_item_id = i.id AND r.status = 'COMPLETED'), 0)
        """, nativeQuery = true)
    java.util.Optional<UUID> verifiedProduct(UUID itemId, UUID customerId);
    @Query(value = "SELECT EXISTS(SELECT 1 FROM product_reviews WHERE order_item_id = :itemId)", nativeQuery = true)
    boolean alreadyReviewed(UUID itemId);
    @Query(value = "SELECT id FROM customers WHERE account_id = :accountId AND NOT deleted AND status = 'ACTIVE'", nativeQuery = true)
    java.util.Optional<UUID> ownCustomer(UUID accountId);
}
