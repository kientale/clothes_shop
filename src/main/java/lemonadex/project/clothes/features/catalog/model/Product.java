package lemonadex.project.clothes.features.catalog.model;

import lemonadex.project.clothes.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "products")
@NoArgsConstructor
@SQLDelete(sql = "UPDATE products SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class Product extends BaseEntity {
    @Column(name = "product_code", nullable = false, length = 80)
    private String productCode;
    @Column(nullable = false, length = 255)
    private String name;
    @Column(nullable = false, length = 300)
    private String slug;
    @Column(columnDefinition = "text")
    private String description;
    @Column(length = 1000)
    private String shortDescription;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    @Column(length = 255)
    private String material;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductGender gender = ProductGender.UNISEX;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal basePrice;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductStatus status = ProductStatus.DRAFT;
    /** Product-level images (variant_id is null) in display order; the first is the primary image. */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @SQLRestriction("variant_id IS NULL")
    @OrderBy("sortOrder ASC")
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<ProductImage> images = new ArrayList<>();
}
