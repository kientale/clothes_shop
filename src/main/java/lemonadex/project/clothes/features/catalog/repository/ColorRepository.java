package lemonadex.project.clothes.features.catalog.repository;

import lemonadex.project.clothes.features.catalog.model.Color;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ColorRepository extends JpaRepository<Color, UUID>, JpaSpecificationExecutor<Color> {
    Optional<Color> findByIdAndDeletedFalse(UUID id);

    List<Color> findAllByDeletedFalse(Sort sort);

    List<Color> findAllByIdInAndDeletedFalse(Collection<UUID> ids);
}
