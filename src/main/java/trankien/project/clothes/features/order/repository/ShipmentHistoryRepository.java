package trankien.project.clothes.features.order.repository;

import trankien.project.clothes.features.order.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.math.BigDecimal;

public interface ShipmentHistoryRepository extends JpaRepository<ShipmentHistory, UUID>, JpaSpecificationExecutor<ShipmentHistory> {
}
