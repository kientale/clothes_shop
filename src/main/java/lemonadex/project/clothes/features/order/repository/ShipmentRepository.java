package lemonadex.project.clothes.features.order.repository;

import lemonadex.project.clothes.features.order.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.math.BigDecimal;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID>, JpaSpecificationExecutor<Shipment> {
    List<Shipment> findAllByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);
    Optional<Shipment> findByShippingProviderAndTrackingCode(String shippingProvider, String trackingCode);
}
