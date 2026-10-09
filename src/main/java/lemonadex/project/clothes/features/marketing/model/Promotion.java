package lemonadex.project.clothes.features.marketing.model;
import lemonadex.project.clothes.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Getter @Setter @NoArgsConstructor @Entity @Table(name = "promotions")
@SQLDelete(sql = "UPDATE promotions SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class Promotion extends BaseEntity {
    @Column(nullable = false, length = 150) private String name;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private PromotionType promotionType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DiscountType discountType;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal discountValue;
    @Column(nullable = false) private Instant startAt;
    @Column(nullable = false) private Instant endAt;
    @Column(nullable = false) private int priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private MarketingStatus status;
    @ElementCollection @CollectionTable(name = "promotion_products", joinColumns = @JoinColumn(name = "promotion_id"))
    @Column(name = "product_id") @BatchSize(size = 50) private Set<UUID> productIds = new LinkedHashSet<>();
    @ElementCollection @CollectionTable(name = "promotion_categories", joinColumns = @JoinColumn(name = "promotion_id"))
    @Column(name = "category_id") @BatchSize(size = 50) private Set<UUID> categoryIds = new LinkedHashSet<>();
}
