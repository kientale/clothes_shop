package lemonadex.project.clothes.features.marketing.repository;
import lemonadex.project.clothes.features.marketing.model.FlashSale;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface FlashSaleRepository extends JpaRepository<FlashSale, UUID>, JpaSpecificationExecutor<FlashSale> {}
