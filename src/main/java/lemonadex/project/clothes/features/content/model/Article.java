package lemonadex.project.clothes.features.content.model;
import lemonadex.project.clothes.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import java.time.Instant;
import java.util.*;
@Getter @Setter @NoArgsConstructor @Entity @Table(name = "articles") @SQLRestriction("deleted = false")
public class Article extends BaseEntity {
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, length = 300) private String slug;
    @Column(columnDefinition = "text") private String thumbnailUrl;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private ArticleType articleType;
    @Column(nullable = false) private UUID authorId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ArticleStatus status = ArticleStatus.DRAFT;
    private Instant publishedAt;
    @ElementCollection @CollectionTable(name = "article_images", joinColumns = @JoinColumn(name = "article_id"))
    @OrderColumn(name = "sort_order") @Column(name = "image_url") @org.hibernate.annotations.BatchSize(size = 50)
    private List<String> images = new ArrayList<>();
}
