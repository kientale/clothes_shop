package lemonadex.project.clothes.features.order.repository;

import lemonadex.project.clothes.features.order.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.math.BigDecimal;

public interface ReturnRepository extends JpaRepository<ReturnRequest, UUID>, JpaSpecificationExecutor<ReturnRequest> {
    @EntityGraph(attributePaths = {"items"})
    Optional<ReturnRequest> findWithItemsById(UUID id);
    @Query(value = """
        SELECT coalesce(sum(i.quantity), 0) FROM return_request_items i JOIN return_requests r ON r.id = i.return_request_id
        WHERE i.order_item_id = :item AND r.status NOT IN ('REJECTED','CANCELLED')
        """, nativeQuery = true)
    long committedQuantity(@Param("item") UUID item);
}
