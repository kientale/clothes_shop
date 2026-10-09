package lemonadex.project.clothes.features.catalog.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/** Row of product_images without timestamps or soft deletion; owned and replaced by its product. */
@Getter
@Setter
@Entity
@Table(name = "product_images")
@NoArgsConstructor
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @Column(nullable = false, columnDefinition = "text")
    private String imageUrl;
    @Column(length = 255)
    private String altText;
    @Column(name = "is_primary", nullable = false)
    private boolean primary;
    @Column(nullable = false)
    private int sortOrder;
}
