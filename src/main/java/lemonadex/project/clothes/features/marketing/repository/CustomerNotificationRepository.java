package lemonadex.project.clothes.features.marketing.repository;
import lemonadex.project.clothes.features.marketing.model.CustomerNotification;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface CustomerNotificationRepository extends JpaRepository<CustomerNotification, UUID>, JpaSpecificationExecutor<CustomerNotification> {
    Optional<CustomerNotification> findByIdAndCustomerId(UUID id, UUID customerId);
    long countByNotificationId(UUID notificationId);
    long countByNotificationIdAndReadTrue(UUID notificationId);
}
