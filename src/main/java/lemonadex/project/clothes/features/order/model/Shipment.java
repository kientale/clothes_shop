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
@Table(name = "shipments")
public class Shipment extends CreatedEntity {
    @Column(nullable = false) private UUID orderId;
    @Column(nullable = false, length = 100) private String shippingProvider;
    @Column(length = 150) private String trackingCode;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal shippingFee;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ShipmentStatus status;
    private Instant shippedAt;
    private Instant deliveredAt;

}
