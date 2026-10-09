package lemonadex.project.clothes.features.review.model;
import lemonadex.project.clothes.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.BatchSize;
import java.time.Instant;
import java.util.*;
@Getter @Setter @NoArgsConstructor @Entity @Table(name = "product_reviews")
@SQLDelete(sql = "UPDATE product_reviews SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class ProductReview extends BaseEntity {
    @Column(nullable = false) private UUID productId;
    @Column(nullable = false) private UUID customerId;
    @Column(nullable = false) private UUID orderItemId;
    @Column(nullable = false) private short rating;
    @Column(columnDefinition = "text") private String comment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ReviewStatus status = ReviewStatus.PENDING;
    @Column(columnDefinition = "text") private String moderationNote;
    private UUID moderatedBy;
    private Instant moderatedAt;
    @ElementCollection @CollectionTable(name = "review_images", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "sort_order") @Column(name = "image_url") @BatchSize(size = 50) private List<String> images = new ArrayList<>();
}
