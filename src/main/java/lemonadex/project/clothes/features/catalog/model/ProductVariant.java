package lemonadex.project.clothes.features.catalog.model;

import lemonadex.project.clothes.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "product_variants")
@NoArgsConstructor
@SQLDelete(sql = "UPDATE product_variants SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class ProductVariant extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "color_id", nullable = false)
    private Color color;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "size_id", nullable = false)
    private Size size;
    @Column(nullable = false, length = 100)
    private String sku;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;
    @Column(precision = 18, scale = 2)
    private BigDecimal compareAtPrice;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CatalogStatus status = CatalogStatus.ACTIVE;
}
