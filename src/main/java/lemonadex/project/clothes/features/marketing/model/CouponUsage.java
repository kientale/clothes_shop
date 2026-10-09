package lemonadex.project.clothes.features.marketing.model;
import jakarta.persistence.*;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Getter @Entity @Table(name = "coupon_usages")
public class CouponUsage {
    @Id private UUID id;
    private UUID couponId;
    private UUID customerId;
    private UUID orderId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal discountAmount;
    private Instant usedAt;
    private boolean released;
    private Instant releasedAt;
}
