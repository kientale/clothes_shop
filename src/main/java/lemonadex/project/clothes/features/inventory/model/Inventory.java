package lemonadex.project.clothes.features.inventory.model;

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
@Table(name = "inventories")
public class Inventory extends CreatedEntity {
    @Column(nullable = false) private UUID warehouseId;
    @Column(nullable = false) private UUID productVariantId;
    @Column(nullable = false) private int quantityOnHand;
    @Column(nullable = false) private int quantityReserved;
    @Column(nullable = false) private Instant updatedAt;

    @PrePersist @PreUpdate
    void timestamp() { updatedAt = Instant.now(); }
}
