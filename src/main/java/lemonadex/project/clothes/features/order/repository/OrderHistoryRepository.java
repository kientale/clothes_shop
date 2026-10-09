package lemonadex.project.clothes.features.order.repository;

import lemonadex.project.clothes.features.order.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.math.BigDecimal;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, UUID>, JpaSpecificationExecutor<OrderHistory> {
}
