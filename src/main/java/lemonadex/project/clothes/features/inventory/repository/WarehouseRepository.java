package lemonadex.project.clothes.features.inventory.repository;

import lemonadex.project.clothes.features.inventory.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.math.BigDecimal;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID>, JpaSpecificationExecutor<Warehouse> {
    Optional<Warehouse> findByIdAndDeletedFalse(UUID id);
    List<Warehouse> findAllByDeletedFalseAndStatusOrderByNameAsc(WarehouseStatus status);
}
