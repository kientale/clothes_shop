package lemonadex.project.clothes.features.inventory.model;

import jakarta.persistence.*;
import lombok.*;
import lemonadex.project.clothes.common.model.BaseEntity;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "warehouses")
@org.hibernate.annotations.SQLDelete(sql = "UPDATE warehouses SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@org.hibernate.annotations.SQLRestriction("deleted = false")
public class Warehouse extends BaseEntity {
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, length = 500) private String address;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private WarehouseStatus status;

}
