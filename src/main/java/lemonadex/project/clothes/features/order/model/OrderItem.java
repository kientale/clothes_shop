package lemonadex.project.clothes.features.order.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @Entity @Table(name = "order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") private PurchaseOrder order;
    @Column(nullable = false) private UUID productVariantId;
    private UUID inventoryId;
    @Column(nullable = false, length = 255) private String productName;
    @Column(nullable = false, length = 100) private String sku;
    @Column(nullable = false, length = 100) private String colorName;
    @Column(nullable = false, length = 50) private String sizeName;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal discountAmount;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal totalAmount;
}
