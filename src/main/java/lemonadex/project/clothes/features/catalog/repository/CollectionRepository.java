package lemonadex.project.clothes.features.catalog.repository;

import lemonadex.project.clothes.features.catalog.model.Collection;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<Collection, UUID>, JpaSpecificationExecutor<Collection> {
    Optional<Collection> findByIdAndDeletedFalse(UUID id);
}
