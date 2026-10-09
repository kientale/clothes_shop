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
@Getter @Setter @NoArgsConstructor @Entity @Table(name = "notifications")
@SQLDelete(sql = "UPDATE notifications SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class Notification extends BaseEntity {
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private NotificationType notificationType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private NotificationTarget targetType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private NotificationStatus status = NotificationStatus.DRAFT;
    private UUID createdBy;
    private Instant publishedAt;
    @ElementCollection @CollectionTable(name = "notification_targets", joinColumns = @JoinColumn(name = "notification_id"))
    @Column(name = "customer_id") @BatchSize(size = 50) private Set<UUID> customerIds = new LinkedHashSet<>();
}
