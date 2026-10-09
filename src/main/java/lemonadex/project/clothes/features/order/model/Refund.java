package lemonadex.project.clothes.features.order.model;

import jakarta.persistence.*;
import lombok.*;
import lemonadex.project.clothes.common.model.CreatedEntity;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refunds")
public class Refund extends CreatedEntity {
    @Column(nullable = false) private UUID returnRequestId;
    @Column(nullable = false) private UUID paymentId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 50) private String refundMethod;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RefundStatus status;
    @Column(columnDefinition = "text") private String reason;
    private UUID processedBy;
    private Instant processedAt;

}
