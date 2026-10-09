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
@Table(name = "payment_transactions")
public class PaymentTransaction extends CreatedEntity {
    @Column(nullable = false) private UUID paymentId;
    @Column(nullable = false, length = 120) private String transactionCode;
    @Column(nullable = false, length = 100) private String provider;
    @Column(length = 255) private String providerTransactionId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentStatus status;

}
