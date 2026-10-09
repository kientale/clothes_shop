package lemonadex.project.clothes.features.order.repository;

import lemonadex.project.clothes.features.order.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.math.BigDecimal;

public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {
    @EntityGraph(attributePaths = {"items"})
    Optional<PurchaseOrder> findWithItemsById(UUID id);
    @Query(value = "SELECT EXISTS (SELECT 1 FROM customers WHERE id = :id AND NOT deleted AND status = 'ACTIVE')", nativeQuery = true)
    boolean activeCustomer(@Param("id") UUID id);
    @Query(value = "SELECT id FROM customers WHERE id = :id AND NOT deleted AND status = 'ACTIVE' FOR SHARE", nativeQuery = true)
    Optional<UUID> lockActiveCustomer(@Param("id") UUID id);
    @Query(value = "SELECT EXISTS (SELECT 1 FROM payment_methods WHERE code = :code AND is_enabled AND NOT deleted)", nativeQuery = true)
    boolean enabledPaymentMethod(@Param("code") String code);
    @Query(value = "SELECT code, name, provider FROM payment_methods WHERE is_enabled AND NOT deleted ORDER BY code", nativeQuery = true)
    List<PaymentMethodOption> paymentMethods();
    interface PaymentMethodOption { String getCode(); String getName(); String getProvider(); }
}
