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
@Getter @Setter @NoArgsConstructor @Entity @Table(name = "banners")
@SQLDelete(sql = "UPDATE banners SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class Banner extends BaseEntity {
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "text") private String imageUrl;
    @Column(columnDefinition = "text") private String linkUrl;
    @Column(nullable = false, length = 50) private String position;
    @Column(nullable = false) private int sortOrder;
    private Instant startAt;
    private Instant endAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private MarketingStatus status;
}
