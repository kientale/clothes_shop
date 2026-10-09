package lemonadex.project.clothes.features.order.repository;

import lemonadex.project.clothes.features.order.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.math.BigDecimal;

public interface RefundRepository extends JpaRepository<Refund, UUID>, JpaSpecificationExecutor<Refund> {
    List<Refund> findAllByPaymentId(UUID paymentId);
    List<Refund> findAllByReturnRequestId(UUID returnRequestId);
    @Query("select coalesce(sum(r.amount), 0) from Refund r, Payment p where r.paymentId = p.id and p.orderId = :id and r.status = 'SUCCEEDED'")
    BigDecimal refundedByOrder(@Param("id") UUID id);
}
