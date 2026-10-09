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
@Table(name = "inventory_transactions")
public class InventoryTransaction extends CreatedEntity {
    @Column(nullable = false) private UUID warehouseId;
    @Column(nullable = false) private UUID productVariantId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private MovementType transactionType;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false) private int quantityBefore;
    @Column(nullable = false) private int quantityAfter;
    @Column(nullable = false) private int reservedBefore;
    @Column(nullable = false) private int reservedAfter;
    @Column(length = 50) private String referenceType;
    private UUID referenceId;
    @Column(columnDefinition = "text") private String note;
    private UUID createdBy;

}
