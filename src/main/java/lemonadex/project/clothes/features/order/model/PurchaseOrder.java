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
@Table(name = "orders")
public class PurchaseOrder extends CreatedEntity {
    @Column(nullable = false, length = 80) private String orderCode;
    @Column(nullable = false) private UUID customerId;
    private UUID warehouseId;
    @Column(length = 50) private String shippingMethodCode;
    @Column(nullable = false) private boolean shippingFeeConfigured;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private OrderStatus orderStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private OrderPaymentStatus paymentStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ShippingStatus shippingStatus;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal subtotal;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal discountAmount;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal shippingFee;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal totalAmount;
    @Column(nullable = false, length = 150) private String recipientName;
    @Column(nullable = false, length = 30) private String recipientPhone;
    @Column(nullable = false, columnDefinition = "text") private String shippingAddress;
    @Column(columnDefinition = "text") private String note;
    /** Email a guest gave at checkout; written once by the storefront, read-only here. */
    @Column(length = 254, insertable = false, updatable = false) private String contactEmail;
    @Column(nullable = false) private Instant placedAt;
    private Instant confirmedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    @Column(nullable = false) private Instant updatedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC") @org.hibernate.annotations.BatchSize(size = 50)
    private java.util.List<OrderItem> items = new java.util.ArrayList<>();

    @PrePersist
    void initialize() { placedAt = Instant.now(); updatedAt = placedAt; }
    @PreUpdate
    void timestamp() { updatedAt = Instant.now(); }
}
