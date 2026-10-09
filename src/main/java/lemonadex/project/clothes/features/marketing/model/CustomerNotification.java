package lemonadex.project.clothes.features.marketing.model;
import lemonadex.project.clothes.common.model.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @Entity @Table(name = "customer_notifications")
public class CustomerNotification extends CreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "notification_id", nullable = false) private Notification notification;
    @Column(nullable = false) private UUID customerId;
    @Column(name = "is_read", nullable = false) private boolean read;
    private Instant readAt;
}
