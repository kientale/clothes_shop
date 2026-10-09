package lemonadex.project.clothes.features.catalog.repository;

import lemonadex.project.clothes.features.catalog.model.ProductVariant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID>, JpaSpecificationExecutor<ProductVariant> {
    @Override
    @EntityGraph(attributePaths = {"product", "color", "size"})
    Page<ProductVariant> findAll(Specification<ProductVariant> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "color", "size"})
    Optional<ProductVariant> findByIdAndDeletedFalse(UUID id);

    @EntityGraph(attributePaths = {"color", "size"})
    List<ProductVariant> findAllByProductIdAndDeletedFalse(UUID productId);

    boolean existsByProductIdAndColorIdAndSizeIdAndDeletedFalse(UUID productId, UUID colorId, UUID sizeId);

    boolean existsByColorIdAndDeletedFalse(UUID colorId);

    boolean existsBySizeIdAndDeletedFalse(UUID sizeId);

    @Query("select v.color.id as id, count(v) as total from ProductVariant v where v.color.id in :ids group by v.color.id")
    List<CatalogRepositories.IdCount> countByColors(@Param("ids") Collection<UUID> ids);

    @Query("select v.size.id as id, count(v) as total from ProductVariant v where v.size.id in :ids group by v.size.id")
    List<CatalogRepositories.IdCount> countBySizes(@Param("ids") Collection<UUID> ids);

    @Query("""
            select v.product.id as productId, count(v) as total, min(v.price) as minPrice, max(v.price) as maxPrice
            from ProductVariant v where v.product.id in :ids group by v.product.id
            """)
    List<VariantStats> statsByProducts(@Param("ids") Collection<UUID> ids);

    /** Id of a soft-deleted variant holding this product/color/size combination, so it can be restored. */
    @Query(value = """
            SELECT id FROM product_variants
            WHERE product_id = :product AND color_id = :color AND size_id = :size AND deleted = TRUE
            """, nativeQuery = true)
    Optional<UUID> findDeletedCombination(@Param("product") UUID product, @Param("color") UUID color, @Param("size") UUID size);

    @Modifying
    @Query(value = "UPDATE product_variants SET deleted = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = :id", nativeQuery = true)
    void restore(@Param("id") UUID id);

    @Modifying
    @Query(value = "UPDATE product_variants SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE product_id = :product AND deleted = FALSE",
            nativeQuery = true)
    int softDeleteByProduct(@Param("product") UUID product);

    interface VariantStats {
        UUID getProductId();

        long getTotal();

        BigDecimal getMinPrice();

        BigDecimal getMaxPrice();
    }
}
