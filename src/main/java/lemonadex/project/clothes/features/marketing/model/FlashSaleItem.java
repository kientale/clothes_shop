package lemonadex.project.clothes.features.marketing.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @Entity @Table(name = "flash_sale_items")
public class FlashSaleItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "flash_sale_id", nullable = false) private FlashSale sale;
    @Column(nullable = false) private UUID productVariantId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal flashPrice;
    @Column(nullable = false) private int quantityLimit;
    @Column(nullable = false) private int soldQuantity;
}
