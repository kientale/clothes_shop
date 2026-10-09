package lemonadex.project.clothes.features.content.model;
import lemonadex.project.clothes.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import java.time.Instant;
import java.util.*;
@Getter @Setter @NoArgsConstructor @Entity @Table(name = "store_policies") @SQLRestriction("deleted = false")
public class StorePolicy extends BaseEntity {
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private PolicyType policyType;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(nullable = false) private int version;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PolicyStatus status = PolicyStatus.DRAFT;
    private UUID updatedBy;
    private Instant activatedAt;
}
