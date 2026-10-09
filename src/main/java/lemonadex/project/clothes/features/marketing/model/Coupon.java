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
@Getter @Setter @NoArgsConstructor @Entity @Table(name = "coupons")
@SQLDelete(sql = "UPDATE coupons SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class Coupon extends BaseEntity {
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 150) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DiscountType discountType;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal discountValue;
    @Column(precision = 18, scale = 2) private BigDecimal maxDiscount;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal minimumOrderValue;
    private Integer usageLimit;
    @Column(nullable = false) private int usageLimitPerCustomer;
    @Column(nullable = false) private int usedCount;
    @Column(nullable = false) private Instant startAt;
    @Column(nullable = false) private Instant endAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private MarketingStatus status;
}
