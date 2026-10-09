package lemonadex.project.clothes.features.marketing.repository;
import lemonadex.project.clothes.features.marketing.model.FlashSaleItem;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, UUID> {
    @Query(value = "SELECT EXISTS(SELECT 1 FROM order_marketing_lines WHERE flash_sale_item_id = :id)", nativeQuery = true)
    boolean hasAllocations(UUID id);
}
