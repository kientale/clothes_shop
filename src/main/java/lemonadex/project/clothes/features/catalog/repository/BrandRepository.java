package lemonadex.project.clothes.features.catalog.repository;

import lemonadex.project.clothes.features.catalog.model.Brand;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID>, JpaSpecificationExecutor<Brand> {
    Optional<Brand> findByIdAndDeletedFalse(UUID id);

    List<Brand> findAllByDeletedFalse(Sort sort);
}
