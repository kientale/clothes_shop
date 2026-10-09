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
@Table(name = "shipment_status_history")
public class ShipmentHistory extends CreatedEntity {
    @Column(nullable = false) private UUID shipmentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ShipmentStatus status;
    @Column(columnDefinition = "text") private String description;

}
