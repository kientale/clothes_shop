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
@Table(name = "return_requests")
@AttributeOverride(name = "createdAt", column = @Column(name = "requested_at", nullable = false, updatable = false))
public class ReturnRequest extends CreatedEntity {
    @Column(nullable = false) private UUID orderId;
    @Column(nullable = false) private UUID customerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ReturnType requestType;
    @Column(nullable = false, columnDefinition = "text") private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ReturnStatus status;
    @Column(columnDefinition = "text") private String note;
    private Instant approvedAt;
    private Instant rejectedAt;
    private Instant completedAt;
    @OneToMany(mappedBy = "request", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC") @org.hibernate.annotations.BatchSize(size = 50)
    private java.util.List<ReturnItem> items = new java.util.ArrayList<>();
    @ElementCollection @CollectionTable(name = "return_images", joinColumns = @JoinColumn(name = "return_request_id"))
    @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private java.util.List<String> images = new java.util.ArrayList<>();

}
