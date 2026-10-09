package lemonadex.project.clothes.features.marketing.repository;
import lemonadex.project.clothes.features.marketing.model.CouponUsage;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface CouponUsageRepository extends JpaRepository<CouponUsage, UUID>, JpaSpecificationExecutor<CouponUsage> {}
