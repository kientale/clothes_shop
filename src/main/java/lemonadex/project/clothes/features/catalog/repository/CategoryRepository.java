package lemonadex.project.clothes.features.catalog.repository;

import lemonadex.project.clothes.features.catalog.model.Category;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category> {
    Optional<Category> findByIdAndDeletedFalse(UUID id);

    List<Category> findAllByDeletedFalse(Sort sort);

    boolean existsByParentIdAndDeletedFalse(UUID parentId);

    @Query("select c.parent.id as id, count(c) as total from Category c where c.parent.id in :ids group by c.parent.id")
    List<CatalogRepositories.IdCount> countChildren(@Param("ids") Collection<UUID> ids);
}
