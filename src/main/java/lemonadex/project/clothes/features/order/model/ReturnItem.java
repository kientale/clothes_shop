package lemonadex.project.clothes.features.order.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @Entity @Table(name = "return_request_items")
public class ReturnItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "return_request_id") private ReturnRequest request;
    @Column(nullable = false) private UUID orderItemId;
    @Column(nullable = false) private int quantity;
    @Column(columnDefinition = "text") private String reason;
    @Column(columnDefinition = "text") private String conditionNote;
    @Enumerated(EnumType.STRING) @Column(length = 30) private ReturnType resolution;
    private UUID replacementVariantId;
    private UUID replacementInventoryId;
}
