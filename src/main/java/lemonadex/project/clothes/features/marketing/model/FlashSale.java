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
@Getter @Setter @NoArgsConstructor @Entity @Table(name = "flash_sales")
@SQLDelete(sql = "UPDATE flash_sales SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class FlashSale extends BaseEntity {
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false) private Instant startAt;
    @Column(nullable = false) private Instant endAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private MarketingStatus status;
    @OneToMany(mappedBy = "sale", cascade = {CascadeType.PERSIST, CascadeType.MERGE}) @OrderBy("id ASC") @BatchSize(size = 50)
    private List<FlashSaleItem> items = new ArrayList<>();
}
