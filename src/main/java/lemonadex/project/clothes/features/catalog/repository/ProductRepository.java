package lemonadex.project.clothes.features.catalog.repository;

import lemonadex.project.clothes.features.catalog.model.Product;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    @Override
    @EntityGraph(attributePaths = {"brand", "category"})
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "category", "images"})
    Optional<Product> findByIdAndDeletedFalse(UUID id);

    @EntityGraph(attributePaths = {"images"})
    List<Product> findAllByIdInAndDeletedFalse(Collection<UUID> ids);

    boolean existsByBrandIdAndDeletedFalse(UUID brandId);

    boolean existsByCategoryIdAndDeletedFalse(UUID categoryId);

    @Query("select p.id from Product p where p.id in :ids")
    List<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);

    /** Soft delete without cascading to the product's images, which are kept with the deleted row. */
    @Modifying
    @Query(value = "UPDATE products SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = :id AND deleted = FALSE",
            nativeQuery = true)
    int softDelete(@Param("id") UUID id);

    @Query("select p.brand.id as id, count(p) as total from Product p where p.brand.id in :ids group by p.brand.id")
    List<CatalogRepositories.IdCount> countByBrands(@Param("ids") Collection<UUID> ids);

    @Query("select p.category.id as id, count(p) as total from Product p where p.category.id in :ids group by p.category.id")
    List<CatalogRepositories.IdCount> countByCategories(@Param("ids") Collection<UUID> ids);
}
