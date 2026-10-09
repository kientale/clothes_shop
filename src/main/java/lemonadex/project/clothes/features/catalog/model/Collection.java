package lemonadex.project.clothes.features.catalog.model;

import lemonadex.project.clothes.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "collections")
@NoArgsConstructor
@SQLDelete(sql = "UPDATE collections SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class Collection extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 200)
    private String slug;
    @Column(columnDefinition = "text")
    private String description;
    @Column(columnDefinition = "text")
    private String imageUrl;
    private Instant startAt;
    private Instant endAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CatalogStatus status = CatalogStatus.ACTIVE;
    /**
     * Ordered product ids; sort_order is the list index. Assign a new list on update: Hibernate then
     * deletes and re-inserts the rows, which avoids primary-key clashes while reordering.
     */
    @ElementCollection
    @CollectionTable(name = "collection_products", joinColumns = @JoinColumn(name = "collection_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "product_id", nullable = false)
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<UUID> productIds = new ArrayList<>();
}
