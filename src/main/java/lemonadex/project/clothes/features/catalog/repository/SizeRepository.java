package lemonadex.project.clothes.features.catalog.repository;

import lemonadex.project.clothes.features.catalog.model.Size;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SizeRepository extends JpaRepository<Size, UUID>, JpaSpecificationExecutor<Size> {
    Optional<Size> findByIdAndDeletedFalse(UUID id);

    List<Size> findAllByDeletedFalse(Sort sort);

    List<Size> findAllByIdInAndDeletedFalse(Collection<UUID> ids);
}
