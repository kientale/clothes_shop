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
@Table(name = "order_status_history")
public class OrderHistory extends CreatedEntity {
    @Column(nullable = false) private UUID orderId;
    @Enumerated(EnumType.STRING) @Column(length = 30) private OrderStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private OrderStatus toStatus;
    @Column(columnDefinition = "text") private String note;
    private UUID changedBy;

}
